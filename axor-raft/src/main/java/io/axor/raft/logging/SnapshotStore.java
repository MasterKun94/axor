package io.axor.raft.logging;

import io.axor.raft.RaftException;
import io.axor.raft.proto.PeerProto;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;

public interface SnapshotStore {
    PeerProto.Snapshot INITIAL_SNAPSHOT = PeerProto.Snapshot.newBuilder()
            .setId(0)
            .build();

    PeerProto.SnapshotResult getLatest() throws RaftException;

    PeerProto.Snapshot getLatestSucceed() throws RaftException;

    ClosableIterator<PeerProto.SnapshotResult> list() throws RaftException;

    List<Long> listId() throws RaftException;

    void save(PeerProto.SnapshotResult result) throws RaftException;

    void install(PeerProto.Snapshot snapshot) throws RaftException;

    PeerProto.SnapshotResult read(long id) throws RaftException;

    void delete(List<Long> ids) throws RaftException;

    interface ClosableIterator<T> extends Iterator<T>, Closeable {
    }
}
