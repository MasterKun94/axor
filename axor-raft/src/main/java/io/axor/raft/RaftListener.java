package io.axor.raft;

import io.axor.api.ActorAddress;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.proto.PeerProto;

public interface RaftListener {

    // common event
    void onStateChange(PeerState from, PeerState to);

    void onTermChange(long from, long to);

    void onLeaderChange(ActorAddress leader);

    // leader event
    void onBecomeLeader();

    void onTxnStarted(PeerProto.LogAppend append);

    void onTxnSuccess();

    void onTxnFailure();

    void onExistLeader();

    // log event
    void onLogCommited(PeerProto.LogEntry entry);

    void onLoadSnapshot(PeerProto.Snapshot snapshot);

    void onInstallSnapshot(PeerProto.InstallSnapshot snapshot);

    EventStage<PeerProto.Snapshot> takeSnapshot(PeerProto.Snapshot snapshot,
                                                EventExecutor executor);
}
