package io.axor.raft;

import io.axor.commons.concurrent.EventPromise;
import io.axor.raft.proto.PeerProto;

import java.io.File;

public interface Statemachine {
    void onLogEntry(PeerProto.LogEntry entry);

    void onQuery(PeerProto.ClientQueryReq req);

    void onSnapshot(SnapshotContext context, EventPromise<PeerProto.Snapshot> promise);

    void onQuery();

    record SnapshotContext(long id, File snapshotDir) {
    }
}
