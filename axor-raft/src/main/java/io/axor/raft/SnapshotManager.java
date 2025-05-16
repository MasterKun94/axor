package io.axor.raft;

import io.axor.commons.concurrent.EventExecutor;
import io.axor.raft.logging.RaftLogging;
import io.axor.raft.logging.SnapshotStore;
import io.axor.raft.proto.PeerProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SnapshotManager {
    private static final Logger LOG = LoggerFactory.getLogger(SnapshotManager.class);

    private final List<IdAndSize> idAndSizeList = new ArrayList<>();

    private final SnapshotStore store;
    private final RaftLogging raftLogging;
    private final int entryLimit;
    private final long bytesLimit;
    private final Duration timeout;
    private final EventExecutor executor;
    private int entryCount = 0;
    private long bytesCount = 0;
    private long currentSnapshotId = -1;

    public SnapshotManager(SnapshotStore store, RaftLogging raftLogging,
                           int entryLimit, long bytesLimit, Duration timeout,
                           EventExecutor executor) {
        this.store = store;
        this.raftLogging = raftLogging;
        this.entryLimit = entryLimit;
        this.bytesLimit = bytesLimit;
        this.timeout = timeout;
        this.executor = executor;
    }

    public RaftLogging.LogEntryListener getListener() {
        return new RaftLogging.LogEntryListener() {
            @Override
            public void appended(PeerProto.LogEntry entry) {
                idAndSizeList.add(new IdAndSize(entry.getId(),
                        entry.getValue().getSerializedSize()));
            }

            @Override
            public void removed(PeerProto.LogId id) {
                IdAndSize last = idAndSizeList.removeLast();
                assert last.id.equals(id);
            }

            @Override
            public void commited(PeerProto.LogId id) {
                IdAndSize first = idAndSizeList.removeFirst();
                assert first.id.equals(id);
                entryCount++;
                bytesCount += first.size;
                if (shouldSnapshot()) {
                    executor.execute(SnapshotManager.this::startSnapshot);
                }
            }

            @Override
            public void installSnapshot(PeerProto.InstallSnapshot snapshot) throws RaftException {
                if (currentSnapshotId > 0) {
                    throw new IllegalStateException("Snapshot is already prepared");
                }
                store.install(snapshot.getSnapshot());
            }
        };
    }

    private boolean shouldSnapshot() {
        return currentSnapshotId == -1 && (entryLimit < entryCount || bytesLimit < bytesCount);
    }

    private void startSnapshot() {
        assert executor.inExecutor();
        if (currentSnapshotId > 0) {
            throw new IllegalStateException("Snapshot is already prepared");
        }
        try {
            currentSnapshotId = store.getLatest().getSnapshot().getId();
        } catch (RaftException e) {
            throw new RuntimeException(e);
        }
        PeerProto.Snapshot snapshot = PeerProto.Snapshot.newBuilder()
                .setId(currentSnapshotId)
                .build();
        raftLogging.takeSnapshot(snapshot, executor)
                .withTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .executor(executor)
                .observe((s, e) -> {
                    PeerProto.SnapshotResult res;
                    if (e != null) {
                        LOG.error("Failed to take snapshot", e);
                        res = PeerProto.SnapshotResult.newBuilder()
                                .setSnapshot(snapshot)
                                .setSuccess(false)
                                .setReason(e.toString())
                                .build();
                    } else {
                        LOG.info("Snapshot taken, id={}", snapshot.getId());
                        res = PeerProto.SnapshotResult.newBuilder()
                                .setSnapshot(snapshot)
                                .setSuccess(true)
                                .build();
                    }
                    try {
                        store.save(res);
                    } catch (RaftException ex) {
                        LOG.error("Failed to save snapshot result", ex);
                    } finally {
                        entryCount = 0;
                        bytesCount = 0;
                        currentSnapshotId = -1;
                    }
                });
    }

    public PeerProto.Snapshot getLatestSnapshot() throws RaftException {
        return store.getLatestSucceed();
    }

    private record IdAndSize(PeerProto.LogId id, long size) {
    }
}
