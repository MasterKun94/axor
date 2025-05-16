package io.axor.raft.logging;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.RaftException;
import io.axor.raft.RocksDBRaftException;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.CommitResult;
import io.axor.raft.proto.PeerProto.LogEntry;
import io.axor.raft.proto.PeerProto.LogId;
import io.axor.raft.proto.PeerProto.LogValue;
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

import static io.axor.api.MessageUtils.loggable;

public class RocksdbRaftLogging implements RaftLogging {

    private final String name;
    private final OptimisticTransactionDB db;
    private final ColumnFamilyHandle cfHandle;
    private final WriteOptions writeOpt;
    private final ReadOptions readOpt;
    private final ByteBuffer keyBuffer;
    private final ByteBuffer valueBuffer;
    private final List<LogEntryListener> listeners = new ArrayList<>();
    private volatile List<LogId> uncommitedIdList = new ArrayList<>();
    private volatile LogId commitedId;
    private volatile LogId startedId;

    public RocksdbRaftLogging(String name, OptimisticTransactionDB db, ColumnFamilyHandle cfHandle,
                              WriteOptions writeOpt, ReadOptions readOpt, boolean bufferDirect,
                              int valueBufferLimit) throws RaftException {
        this.name = name;
        this.db = db;
        this.keyBuffer = bufferDirect ?
                ByteBuffer.allocateDirect(16) :
                ByteBuffer.allocate(16);
        this.valueBuffer = bufferDirect ?
                ByteBuffer.allocateDirect(valueBufferLimit) :
                ByteBuffer.allocate(valueBufferLimit);
        this.cfHandle = cfHandle;
        this.writeOpt = writeOpt;
        this.readOpt = readOpt;
        byte[] bytes;
        try {
            bytes = db.get(name.getBytes());
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        commitedId = bytes == null ? INITIAL_LOG_ID : LogUtils.toId(bytes);
        refreshStartedId();
        try (RocksIterator iter = db.newIterator(cfHandle, readOpt)) {
            List<LogId> uncommited = new ArrayList<>();
            for (iter.seek(LogUtils.toBytes(commitedId)); iter.isValid(); iter.next()) {
                iter.key(keyBuffer.clear());
                uncommited.add(LogUtils.toId(keyBuffer));
            }
            if (!uncommited.isEmpty()) {
                uncommitedIdList = Collections.unmodifiableList(uncommited);
            }
        }
    }

    private void refreshStartedId() {
        LogId commitedId = this.commitedId;
        if (commitedId == INITIAL_LOG_ID) {
            startedId = commitedId;
        }
        try (RocksIterator iter = db.newIterator(cfHandle, readOpt)) {
            iter.seekToFirst();
            startedId = iter.isValid() ? LogUtils.toId(iter.key()) : INITIAL_LOG_ID;
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
    public AppendResult append(LogId prevLogId, LogEntry entry) throws RaftException {
        return append(prevLogId, Collections.singletonList(entry));
    }

    private AppendResult result(AppendResult.Status status) {
        AppendResult.Builder builder = AppendResult.newBuilder()
                .setStatus(status)
                .setCommited(commitedId);
        if (uncommitedIdList != null) {
            builder.addAllUncommited(uncommitedIdList);
        }
        return builder.build();
    }

    private CommitResult result(CommitResult.Status status) {
        CommitResult.Builder builder = CommitResult.newBuilder()
                .setStatus(status)
                .setCommited(commitedId);
        if (uncommitedIdList != null) {
            builder.addAllUncommited(uncommitedIdList);
        }
        return builder.build();
    }

    @Override
    public AppendResult append(LogId prevLogId, List<LogEntry> entries) throws RaftException {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("empty entries");
        }
        List<LogId> uncommitedIdList =
                this.uncommitedIdList == null || this.uncommitedIdList.isEmpty() ?
                        new ArrayList<>(entries.size()) : new ArrayList<>(this.uncommitedIdList);
        List<TxnAction> actionList = new ArrayList<>();
        ByteBuffer keyBuffer = this.keyBuffer;
        ByteBuffer valueBuffer = this.valueBuffer;
        for (LogEntry entry : entries) {
            LogId id = entry.getId();
            LogValue value = entry.getValue();
            if (id.getIndex() <= commitedId.getIndex()) {
                keyBuffer.clear();
                valueBuffer.clear();
                try {
                    int i = db.get(cfHandle, readOpt, LogUtils.toBytes(id, keyBuffer), valueBuffer);
                    if (i == RocksDB.NOT_FOUND) {
                        return result(AppendResult.Status.INDEX_EXPIRED);
                    }
                    if (LogUtils.toValue(valueBuffer).equals(value)) {
                        // already commited, ignore
                        continue;
                    }
                } catch (RocksDBException e) {
                    throw new RocksDBRaftException(e);
                }
                return result(AppendResult.Status.INDEX_EXPIRED);
            }
            if (id.getTerm() < commitedId.getTerm()) {
                return result(AppendResult.Status.TERM_EXPIRED);
            }
            LogId uncommitedId = uncommitedIdList.isEmpty() ? commitedId :
                    uncommitedIdList.getLast();
            long l = id.getIndex() - uncommitedId.getIndex();
            if (l > 1) {
                return result(AppendResult.Status.INDEX_EXCEEDED);
            }
            if (l != 1) {
                int off = (int) (id.getIndex() - commitedId.getIndex());
                while (uncommitedIdList.size() >= off) {
                    LogId removedId = uncommitedIdList.removeLast();
                    actionList.add(new RemoveEntry(removedId));
                }
                uncommitedId = uncommitedIdList.isEmpty() ? commitedId :
                        uncommitedIdList.getLast();
            }
            if (!uncommitedId.equals(prevLogId)) {
                return result(AppendResult.Status.PREV_ID_NOT_MATCH);
            }
            if (id.getTerm() < uncommitedId.getTerm()) {
                return result(AppendResult.Status.TERM_EXPIRED);
            }
            uncommitedIdList.add(entry.getId());
            actionList.add(new PutEntry(entry));
            prevLogId = entry.getId();
        }
        if (actionList.isEmpty()) {
            resetUncommited();
            return result(AppendResult.Status.NO_ACTION);
        }
        try (var txn = db.beginTransaction(writeOpt)) {
            for (TxnAction action : actionList) {
                action.apply(txn);
            }
            txn.commit();
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        if (!listeners.isEmpty()) {
            for (TxnAction action : actionList) {
                for (LogEntryListener listener : listeners) {
                    action.apply(listener);
                }
            }
        }
        this.uncommitedIdList = Collections.unmodifiableList(uncommitedIdList);
        return result(AppendResult.Status.SUCCESS);
    }

    @Override
    public CommitResult commit(LogId commitAtId) throws RaftException {
        LogId prev = commitedId;
        if (prev.equals(commitAtId)) {
            return result(CommitResult.Status.NO_ACTION);
        }
        if (prev.getIndex() > commitAtId.getIndex()) {
            if (db.keyExists(cfHandle, readOpt, LogUtils.toBytes(commitAtId))) {
                return result(CommitResult.Status.NO_ACTION);
            }
        }
        var uncommitedIdList = this.uncommitedIdList;
        if (uncommitedIdList == null || uncommitedIdList.isEmpty()) {
            return result(CommitResult.Status.NO_VALUE);
        }
        int idx = uncommitedIdList.indexOf(commitAtId);
        if (idx == -1) {
            if (uncommitedIdList.getLast().getIndex() < commitedId.getIndex()) {
                return result(CommitResult.Status.INDEX_EXCEEDED);
            }
            return result(CommitResult.Status.ILLEGAL_STATE);
        }
        try {
            db.put(name.getBytes(), LogUtils.toBytes(commitAtId));
            commitedId = commitAtId;
            if (idx + 1 == uncommitedIdList.size()) {
                for (LogId logId : uncommitedIdList) {
                    for (LogEntryListener listener : listeners) {
                        listener.commited(logId);
                    }
                }
                this.uncommitedIdList = null;
            } else {
                List<LogId> sliced = uncommitedIdList.subList(idx + 1,
                        uncommitedIdList.size());
                for (LogId logId : uncommitedIdList.subList(0, idx + 1)) {
                    for (LogEntryListener listener : listeners) {
                        listener.commited(logId);
                    }
                }
                this.uncommitedIdList = List.copyOf(sliced);
            }
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        if (prev.equals(INITIAL_LOG_ID)) {
            refreshStartedId();
        }
        return result(CommitResult.Status.SUCCESS);
    }

    @Override
    public List<LogEntry> read(LogId start, boolean includeStart, boolean includeUncommited,
                               int entryLimit, int sizeLimit) throws RaftException {
        long endIndex = includeUncommited ? logEndId().getIndex() : commitedId.getIndex();
        long available = endIndex == INITIAL_LOG_ID.getIndex() ? 0 :
                includeStart ? endIndex - start.getIndex() + 1 : endIndex - start.getIndex();
        int limit = Math.min((int) available, entryLimit);
        if (limit <= 0) {
            return Collections.emptyList();
        }
        try (RocksIterator iter = db.newIterator(cfHandle, readOpt)) {
            keyBuffer.clear();
            iter.seek(LogUtils.toBytes(start, keyBuffer));
            List<LogEntry> entries = new ArrayList<>();
            int cnt = 0;
            int size = 0;
            if (!includeStart) {
                iter.next();
            }
            while (iter.isValid() && cnt < limit && size < sizeLimit) {
                keyBuffer.clear();
                iter.key(keyBuffer);
                LogId id = LogUtils.toId(keyBuffer);
                valueBuffer.clear();
                iter.value(valueBuffer);
                LogValue value = LogUtils.toValue(valueBuffer);
                entries.add(LogEntry.newBuilder().setId(id).setValue(value).build());
                cnt++;
                iter.next();
                size += value.getSerializedSize();
            }
            return entries;
        }
    }

    @Override
    public List<LogEntry> readForSync(LogId commited, List<LogId> uncommited, int entryLimit,
                                      int sizeLimit) throws RaftException {
        List<LogId> list;
        if (uncommited.isEmpty()) {
            list = Collections.singletonList(commited);
        } else {
            list = new ArrayList<>(uncommited.size() + 1);
            list.addAll(uncommited.reversed());
            list.add(commited);
        }
        LogId logEndId = logEndId();
        if (logEndId.getIndex() < commited.getIndex()) {
            throw new IllegalArgumentException("illegal input logId");
        }
        long prevIndex = -1;
        long prevTerm = -1;
        for (LogId logId : list) {

            if (prevIndex != -1 && prevIndex <= logId.getIndex()) {
                throw new IllegalArgumentException("illegal input logId index");
            }
            if (prevTerm != -1 && prevTerm <= logId.getTerm()) {
                throw new IllegalArgumentException("illegal input logId term");
            }
            prevIndex = logId.getIndex();
            prevTerm = logId.getTerm();
            if (db.keyExists(cfHandle, readOpt, LogUtils.toBytes(logId))) {
                return read(logId, false, true, entryLimit, sizeLimit);
            }
        }
        throw new IllegalArgumentException("logId not match");
    }

    @Override
    public void resetUncommited() throws RaftException {
        var uncommitedIdList = this.uncommitedIdList;
        if (uncommitedIdList == null || uncommitedIdList.isEmpty()) {
            return;
        }
        try (Transaction txn = db.beginTransaction(writeOpt)) {
            for (LogId logId : uncommitedIdList) {
                txn.singleDelete(cfHandle, LogUtils.toBytes(logId));
            }
            txn.commit();
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        for (LogId logId : uncommitedIdList) {
            for (LogEntryListener listener : listeners) {
                listener.removed(logId);
            }
        }
        this.uncommitedIdList = null;
    }

    @Override
    public void expire(LogId before) throws RaftException {
        byte[] b = LogUtils.toBytes(before);
        if (!db.keyExists(cfHandle, b)) {
            throw new IllegalArgumentException(before + " not exists");
        }
        try {
            List<byte[]> range = List.of(LogUtils.toBytes(INITIAL_LOG_ID), b);
            db.deleteFilesInRanges(cfHandle, range, false);
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        refreshStartedId();
    }

    @Override
    public void loadSnapshot(PeerProto.Snapshot snapshot) throws RaftException {
        LogId lodId = snapshot.getLogId();
        if (lodId.getIndex() > commitedId.getIndex()) {
            throw new IllegalStateException("snapshot " + loggable(lodId) + " " +
                                            "greater than commited " + loggable(lodId));
        }
        List<LogEntryListener> listeners = this.listeners;
        for (LogEntryListener listener : listeners) {
            listener.loadSnapshot(snapshot);
        }
        long nextIndex = lodId.getIndex() + 1;
        try (RocksIterator iter = db.newIterator(cfHandle, readOpt)) {
            if (lodId.equals(INITIAL_LOG_ID)) {
                iter.seekToFirst();
            } else {
                iter.seek(LogUtils.toBytes(lodId));
                iter.next();
            }
            ByteBuffer valueBuffer = this.valueBuffer;
            ByteBuffer keyBuffer = this.keyBuffer;
            while (iter.isValid()) {
                valueBuffer.clear();
                keyBuffer.clear();
                iter.key(keyBuffer);
                iter.value(valueBuffer);
                LogId key = LogUtils.toId(keyBuffer);
                LogValue value = LogUtils.toValue(valueBuffer);
                if (nextIndex != key.getIndex()) {
                    throw new IllegalStateException("unexpected log index");
                }
                for (LogEntryListener listener : listeners) {
                    listener.appended(LogEntry.newBuilder()
                            .setId(key)
                            .setValue(value)
                            .build());
                }
                if (key.getIndex() <= commitedId.getIndex()) {
                    for (LogEntryListener listener : listeners) {
                        listener.commited(key);
                    }
                }
            }
        }
    }

    @Override
    public void installSnapshot(PeerProto.InstallSnapshot snapshot) throws RaftException {
        LogId logEndId = logEndId();
        try {
            db.deleteFilesInRanges(cfHandle,
                    List.of(LogUtils.toBytes(INITIAL_LOG_ID), LogUtils.toBytes(logEndId)),
                    true);
            db.put(name.getBytes(), LogUtils.toBytes(snapshot.getCommitedLogId()));
            startedId = commitedId = snapshot.getCommitedLogId();
            uncommitedIdList = null;
        } catch (RocksDBException e) {
            throw new RocksDBRaftException(e);
        }
        for (LogEntryListener listener : listeners) {
            listener.installSnapshot(snapshot);
        }
    }

    @Override
    public EventStage<PeerProto.Snapshot> takeSnapshot(PeerProto.Snapshot snapshot,
                                                       EventExecutor executor) {
        EventStage<PeerProto.Snapshot> stage = EventStage.succeed(snapshot.toBuilder()
                .setLogId(commitedId)
                .build(), executor);
        for (LogEntryListener listener : listeners) {
            stage = stage.flatmap(s -> listener.takeSnapshot(s, executor));
        }
        return stage;
    }

    @Override
    public void addListener(LogEntryListener listener) {
        listeners.add(listener);
    }

    private sealed interface TxnAction {
        void apply(Transaction txn) throws RaftException, RocksDBException;

        void apply(LogEntryListener listener) throws RaftException;
    }

    private final class PutEntry implements TxnAction {
        private final LogEntry entry;

        private PutEntry(LogEntry entry) {
            this.entry = entry;
        }

        @Override
        public void apply(Transaction txn) throws RaftException, RocksDBException {
            keyBuffer.clear();
            valueBuffer.clear();
            ByteBuffer key = LogUtils.toBytes(entry.getId(), keyBuffer);
            ByteBuffer value = LogUtils.toBytes(entry.getValue(), valueBuffer);
            txn.put(cfHandle, key, value);
        }

        @Override
        public void apply(LogEntryListener listener) throws RaftException {
            listener.appended(entry);
        }
    }

    private final class RemoveEntry implements TxnAction {
        private final LogId id;

        private RemoveEntry(LogId id) {
            this.id = id;
        }

        @Override
        public void apply(Transaction txn) throws RocksDBException {
            txn.delete(cfHandle, LogUtils.toBytes(id));
        }

        @Override
        public void apply(LogEntryListener listener) throws RaftException {
            listener.removed(id);
        }
    }
}
