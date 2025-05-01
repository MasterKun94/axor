package io.axor.cp.raft.logging;

import com.google.protobuf.ByteString;
import io.axor.cp.messages.AppendStatus;
import io.axor.cp.messages.CommitStatus;
import io.axor.cp.messages.LogEntry;
import io.axor.cp.messages.LogEntryId;
import io.axor.cp.raft.LogUtils;
import io.axor.cp.raft.RaftException;
import io.axor.cp.raft.RocksDBRaftException;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
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
    private volatile List<LogEntryId> uncommitedIdList = new ArrayList<>();
    private volatile LogEntryId commitedId;
    private volatile LogEntryId startedId;

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
        commitedId = bytes == null ? LogEntryId.INITIAL : LogUtils.toId(bytes);
        refreshStartedId();
        try (RocksIterator iter = db.newIterator(cfHandle)) {
            List<LogEntryId> uncommited = new ArrayList<>();
            for (iter.seek(LogUtils.toBytes(commitedId)); iter.isValid(); iter.next()) {
                uncommited.add(LogUtils.toId(iter.key()));
            }
            if (!uncommited.isEmpty()) {
                uncommitedIdList = uncommited;
            }
        }
    }

    private void refreshStartedId() {
        LogEntryId commitedId = this.commitedId;
        if (commitedId == LogEntryId.INITIAL) {
            startedId = commitedId;
        }
        try (RocksIterator iter = db.newIterator(cfHandle, readOpt)) {
            iter.seekToFirst();
            startedId = iter.isValid() ? LogUtils.toId(iter.key()) : LogEntryId.INITIAL;
        }
    }

    @Override
    public LogEntryId startedId() {
        return startedId;
    }

    @Override
    public LogEntryId commitedId() {
        return commitedId;
    }

    @Override
    public LogEntryId uncommitedId() {
        List<LogEntryId> uncommitedIdList = this.uncommitedIdList;
        return uncommitedIdList == null || uncommitedIdList.isEmpty() ? commitedId :
                uncommitedIdList.getLast();
    }

    @Override
    public AppendStatus append(LogEntry entry) throws RaftException {
        return append(Collections.singletonList(entry));
    }

    private void putEntry(Transaction txn, LogEntry entry, ByteBuffer reuse) throws RocksDBException {
        reuse.clear();
        ByteBuffer key = LogUtils.toBytes(entry.id(), reuse);
        ByteBuffer value = LogUtils.dataBytes(entry, reuse);
        txn.put(cfHandle, key, value);
    }

    private void removeEntry(Transaction txn, LogEntryId id) throws RocksDBException {
        txn.delete(cfHandle, LogUtils.toBytes(id));
    }

    @Override
    public AppendStatus append(List<LogEntry> entries) throws RaftException {
        if (Thread.currentThread() != writerThread) {
            throw new IllegalArgumentException("not writer thread");
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("empty entries");
        }
        List<LogEntryId> uncommitedIdList =
                this.uncommitedIdList == null || this.uncommitedIdList.isEmpty() ?
                        new ArrayList<>(entries.size()) : new ArrayList<>(this.uncommitedIdList);
        List<TxnAction> actionList = new ArrayList<>();
        ByteBuffer reuse = reuseBufferTl.get();
        for (LogEntry entry : entries) {
            LogEntryId id = entry.id();
            if (id.term() < commitedId.term()) {
                return AppendStatus.TERM_EXPIRED;
            }
            if (id.index() <= commitedId.index()) {
                return AppendStatus.INDEX_EXPIRED;
            }
            LogEntryId uncommitedId = uncommitedIdList.isEmpty() ? commitedId :
                    uncommitedIdList.getLast();
            long l = id.index() - uncommitedId.index();
            if (l > 1) {
                return AppendStatus.INDEX_EXCEEDED;
            }
            if (l != 1) {
                int off = (int) (id.index() - commitedId.index());
                while (uncommitedIdList.size() >= off) {
                    LogEntryId removedId = uncommitedIdList.removeLast();
                    actionList.add(txn -> removeEntry(txn, removedId));
                }
                uncommitedId = uncommitedIdList.isEmpty() ? commitedId :
                        uncommitedIdList.getLast();
            }
            if (id.term() < uncommitedId.term()) {
                return AppendStatus.TERM_EXPIRED;
            }
            uncommitedIdList.add(entry.id());
            actionList.add(txn -> putEntry(txn, entry, reuse));
        }
        try (var txn = db.beginTransaction(writeOpt)) {
            for (TxnAction action : actionList) {
                action.apply(txn);
            }
            txn.commit();
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        this.uncommitedIdList = uncommitedIdList;
        return AppendStatus.SUCCESS;
    }

    @Override
    public CommitStatus commit(LogEntryId commitAtId) throws RaftException {
        if (Thread.currentThread() != writerThread) {
            throw new IllegalArgumentException("not writer thread");
        }
        int idx = this.uncommitedIdList.indexOf(commitAtId);
        if (idx == -1) {
            return CommitStatus.ILLEGAL_STATE;
        }
        LogEntryId prev = commitedId;
        try {
            db.put(name.getBytes(), LogUtils.toBytes(commitAtId));
            commitedId = commitAtId;
            if (idx + 1 == uncommitedIdList.size()) {
                this.uncommitedIdList = null;
            } else {
                List<LogEntryId> sliced = uncommitedIdList.subList(idx + 1,
                        uncommitedIdList.size());
                this.uncommitedIdList = new ArrayList<>(sliced);
            }
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        if (prev.equals(LogEntryId.INITIAL)) {
            refreshStartedId();
        }
        return CommitStatus.SUCCESS;
    }

    @Override
    public List<LogEntry> read(LogEntryId startInclude, int entryLimit, int sizeLimit) throws RaftException {
        int max = (int) Math.min(commitedId.index() - startInclude.index(), entryLimit);
        if (max <= 0) {
            return Collections.emptyList();
        }
        ByteBuffer reuse = reuseBufferTl.get();
        reuse.clear();
        RocksIterator iter = db.newIterator(cfHandle, readOpt);
        iter.seek(LogUtils.toBytes(startInclude, reuse));
        List<LogEntry> entries = new ArrayList<>();
        int cnt = 0;
        int size = 0;
        iter.seek(LogUtils.toBytes(startInclude, reuse));
        while (iter.isValid() && cnt < entryLimit && size < sizeLimit) {
            reuse.clear();
            iter.key(reuse);
            LogEntryId id = LogUtils.toId(reuse);
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
    public void resetUncommited() {
        uncommitedIdList = null;
    }

    @Override
    public void expire(LogEntryId before) throws RaftException {
        byte[] b = LogUtils.toBytes(before);
        if (!db.keyExists(cfHandle, b)) {
            throw new IllegalArgumentException(before + " not exists");
        }
        try {
            db.deleteFilesInRanges(cfHandle, List.of(LogUtils.toBytes(LogEntryId.INITIAL), b),
                    false);
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        refreshStartedId();
    }

    private interface TxnAction {
        void apply(Transaction txn) throws RaftException, RocksDBException;
    }
}
