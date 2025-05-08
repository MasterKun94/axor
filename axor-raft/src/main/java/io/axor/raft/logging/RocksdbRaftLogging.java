package io.axor.raft.logging;

import com.google.protobuf.ByteString;
import io.axor.raft.AppendStatus;
import io.axor.raft.CommitStatus;
import io.axor.raft.LogEntry;
import io.axor.raft.LogId;
import io.axor.raft.RaftException;
import io.axor.raft.RocksDBRaftException;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RocksdbRaftLogging implements RaftLogging {

    private final String name;
    private final OptimisticTransactionDB db;
    private final ColumnFamilyHandle cfHandle;
    private final WriteOptions writeOpt;
    private final ReadOptions readOpt;
    private final Thread writerThread;
    private final ThreadLocal<ByteBuffer> reuseBufferTl;
    private volatile List<LogId> uncommitedIdList = new ArrayList<>();
    private volatile LogId commitedId;
    private volatile LogId startedId;

    public RocksdbRaftLogging(String name, OptimisticTransactionDB db, ColumnFamilyHandle cfHandle,
                              WriteOptions writeOpt, ReadOptions readOpt, Thread writerThread,
                              ThreadLocal<ByteBuffer> reuseBufferTl) throws RaftException {
        this.name = name;
        this.db = db;
        this.reuseBufferTl = reuseBufferTl;
        this.cfHandle = cfHandle;
        this.writeOpt = writeOpt;
        this.readOpt = readOpt;
        this.writerThread = writerThread;
        byte[] bytes;
        try {
            bytes = db.get(name.getBytes());
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        commitedId = bytes == null ? LogId.INITIAL : LogUtils.toId(bytes);
        refreshStartedId();
        try (RocksIterator iter = db.newIterator(cfHandle)) {
            List<LogId> uncommited = new ArrayList<>();
            for (iter.seek(LogUtils.toBytes(commitedId)); iter.isValid(); iter.next()) {
                uncommited.add(LogUtils.toId(iter.key()));
            }
            if (!uncommited.isEmpty()) {
                uncommitedIdList = Collections.unmodifiableList(uncommited);
            }
        }
    }

    private void refreshStartedId() {
        LogId commitedId = this.commitedId;
        if (commitedId == LogId.INITIAL) {
            startedId = commitedId;
        }
        try (RocksIterator iter = db.newIterator(cfHandle, readOpt)) {
            iter.seekToFirst();
            startedId = iter.isValid() ? LogUtils.toId(iter.key()) : LogId.INITIAL;
        }
    }

    @Override
    public LogId startedId() {
        return startedId;
    }

    @Override
    public LogId commitedId() {
        return commitedId;
    }

    @Override
    public List<LogId> uncommitedId() {
        return uncommitedIdList == null ? Collections.emptyList() : uncommitedIdList;
    }

    @Override
    public LogId logEndId() {
        List<LogId> uncommitedIdList = uncommitedId();
        return uncommitedIdList.isEmpty() ? commitedId : uncommitedIdList.getLast();
    }

    @Override
    public AppendResult append(LogEntry entry) throws RaftException {
        return append(Collections.singletonList(entry));
    }

    private void putEntry(Transaction txn, LogEntry entry, ByteBuffer reuse) throws RocksDBException {
        reuse.clear();
        ByteBuffer key = LogUtils.toBytes(entry.id(), reuse);
        ByteBuffer value = LogUtils.dataBytes(entry, reuse);
        txn.put(cfHandle, key, value);
    }

    private void removeEntry(Transaction txn, LogId id) throws RocksDBException {
        txn.delete(cfHandle, LogUtils.toBytes(id));
    }

    private AppendResult result(AppendStatus status) {
        return new AppendResult(status, commitedId, uncommitedId());
    }


    private CommitResult result(CommitStatus status) {
        return new CommitResult(status, commitedId);
    }

    @Override
    public AppendResult append(List<LogEntry> entries) throws RaftException {
        if (Thread.currentThread() != writerThread) {
            throw new IllegalArgumentException("not writer thread");
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("empty entries");
        }
        List<LogId> uncommitedIdList =
                this.uncommitedIdList == null || this.uncommitedIdList.isEmpty() ?
                        new ArrayList<>(entries.size()) : new ArrayList<>(this.uncommitedIdList);
        List<TxnAction> actionList = new ArrayList<>();
        ByteBuffer reuse = reuseBufferTl.get();
        for (LogEntry entry : entries) {
            LogId id = entry.id();
            if (id.index() <= commitedId.index()) {
                reuse.clear();
                ByteBuffer bKey = LogUtils.toBytes(id, reuse);
                ByteBuffer slice = reuse.slice(16, reuse.remaining());
                try {
                    int i = db.get(cfHandle, readOpt, bKey, slice);
                    if (i == RocksDB.NOT_FOUND) {
                        return result(AppendStatus.INDEX_EXPIRED);
                    }
                    if (entry.data().equals(ByteString.copyFrom(slice))) {
                        // append a commited entry, ignore
                        continue;
                    }
                } catch (RocksDBException e) {
                    throw new RocksDBRaftException(e);
                }
                return result(AppendStatus.INDEX_EXPIRED);
            }
            if (id.term() < commitedId.term()) {
                return result(AppendStatus.TERM_EXPIRED);
            }
            LogId uncommitedId = uncommitedIdList.isEmpty() ? commitedId :
                    uncommitedIdList.getLast();
            long l = id.index() - uncommitedId.index();
            if (l > 1) {
                return result(AppendStatus.INDEX_EXCEEDED);
            }
            if (l != 1) {
                int off = (int) (id.index() - commitedId.index());
                while (uncommitedIdList.size() >= off) {
                    LogId removedId = uncommitedIdList.removeLast();
                    actionList.add(txn -> removeEntry(txn, removedId));
                }
                uncommitedId = uncommitedIdList.isEmpty() ? commitedId :
                        uncommitedIdList.getLast();
            }
            if (id.term() < uncommitedId.term()) {
                return result(AppendStatus.TERM_EXPIRED);
            }
            uncommitedIdList.add(entry.id());
            actionList.add(txn -> putEntry(txn, entry, reuse));
        }
        if (actionList.isEmpty()) {
            resetUncommited();
            return result(AppendStatus.NO_ACTION);
        }
        try (var txn = db.beginTransaction(writeOpt)) {
            for (TxnAction action : actionList) {
                action.apply(txn);
            }
            txn.commit();
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        this.uncommitedIdList = Collections.unmodifiableList(uncommitedIdList);
        return result(AppendStatus.SUCCESS);
    }

    @Override
    public CommitResult commit(LogId commitAtId) throws RaftException {
        if (Thread.currentThread() != writerThread) {
            throw new IllegalArgumentException("not writer thread");
        }
        LogId prev = commitedId;
        if (prev.equals(commitAtId)) {
            return result(CommitStatus.NO_ACTION);
        }
        if (prev.index() > commitAtId.index()) {
            if (db.keyExists(cfHandle, readOpt, LogUtils.toBytes(commitAtId))) {
                return result(CommitStatus.NO_ACTION);
            }
        }
        if (uncommitedIdList.isEmpty()) {
            return result(CommitStatus.NO_VALUE);
        }
        int idx = this.uncommitedIdList.indexOf(commitAtId);
        if (idx == -1) {
            if (uncommitedIdList.getLast().index() < commitedId.index()) {
                return result(CommitStatus.INDEX_EXCEEDED);
            }
            return result(CommitStatus.ILLEGAL_STATE);
        }
        try {
            db.put(name.getBytes(), LogUtils.toBytes(commitAtId));
            commitedId = commitAtId;
            if (idx + 1 == uncommitedIdList.size()) {
                this.uncommitedIdList = null;
            } else {
                List<LogId> sliced = uncommitedIdList.subList(idx + 1,
                        uncommitedIdList.size());
                this.uncommitedIdList = List.copyOf(sliced);
            }
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        if (prev.equals(LogId.INITIAL)) {
            refreshStartedId();
        }
        return result(CommitStatus.SUCCESS);
    }

    @Override
    public List<LogEntry> read(LogId start, boolean includeStart, boolean includeUncommited,
                               int entryLimit, int sizeLimit) {
        long endIndex = includeUncommited ? logEndId().index() : commitedId.index();
        long available = endIndex == LogId.INITIAL.index() ? 0 :
                includeStart ? endIndex - start.index() + 1 : endIndex - start.index();
        int limit = Math.min((int) available, entryLimit);
        if (limit <= 0) {
            return Collections.emptyList();
        }
        ByteBuffer reuse = reuseBufferTl.get();
        reuse.clear();
        RocksIterator iter = db.newIterator(cfHandle, readOpt);
        iter.seek(LogUtils.toBytes(start, reuse));
        List<LogEntry> entries = new ArrayList<>();
        int cnt = 0;
        int size = 0;
        if (!includeStart) {
            iter.next();
        }
        while (iter.isValid() && cnt < limit && size < sizeLimit) {
            reuse.clear();
            iter.key(reuse);
            LogId id = LogUtils.toId(reuse);
            reuse.clear();
            iter.value(reuse);
            LogEntry entry = new LogEntry(id, ByteString.copyFrom(reuse));
            entries.add(entry);
            cnt++;
            iter.next();
            size += entry.data().size();
        }
        return entries;
    }

    @Override
    public List<LogEntry> readForSync(LogId commited, List<LogId> uncommited, int entryLimit,
                                      int sizeLimit) {
        List<LogId> list;
        if (uncommited.isEmpty()) {
            list = Collections.singletonList(commited);
        } else {
            list = new ArrayList<>(uncommited.size() + 1);
            list.addAll(uncommited.reversed());
            list.add(commited);
        }
        LogId logEndId = logEndId();
        if (logEndId.index() < commited.index()) {
            throw new IllegalArgumentException("illegal input logId");
        }
        long prevIndex = -1;
        long prevTerm = -1;
        for (LogId logId : list) {

            if (prevIndex != -1 && prevIndex <= logId.index()) {
                throw new IllegalArgumentException("illegal input logId index");
            }
            if (prevTerm != -1 && prevTerm <= logId.term()) {
                throw new IllegalArgumentException("illegal input logId term");
            }
            prevIndex = logId.index();
            prevTerm = logId.term();
            if (db.keyExists(cfHandle, readOpt, LogUtils.toBytes(logId))) {
                return read(logId, false, true, entryLimit, sizeLimit);
            }
        }
        throw new IllegalArgumentException("logId not match");
    }

    @Override
    public void resetUncommited() throws RaftException {
        if (uncommitedIdList == null || uncommitedIdList.isEmpty()) {
            return;
        }
        try (Transaction txn = db.beginTransaction(writeOpt)) {
            for (LogId logId : uncommitedIdList) {
                txn.singleDelete(cfHandle, LogUtils.toBytes(logId));
            }
            uncommitedIdList = null;
            txn.commit();
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
    }

    @Override
    public void expire(LogId before) throws RaftException {
        byte[] b = LogUtils.toBytes(before);
        if (!db.keyExists(cfHandle, b)) {
            throw new IllegalArgumentException(before + " not exists");
        }
        try {
            List<byte[]> range = List.of(LogUtils.toBytes(LogId.INITIAL), b);
            db.deleteFilesInRanges(cfHandle, range, false);
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        refreshStartedId();
    }

    private interface TxnAction {
        void apply(Transaction txn) throws RaftException, RocksDBException;
    }
}
