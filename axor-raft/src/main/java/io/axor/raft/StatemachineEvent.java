package io.axor.raft;

import io.axor.raft.proto.PeerProto;

import java.util.List;

public interface StatemachineEvent {
    record LogEntries(List<PeerProto.LogEntry> entries) {
    }

    record InstallSnapshot(PeerProto.InstallSnapshot snapshot) {
    }

    record TakeSnapshot(PeerProto.Snapshot snapshot) {
    }

}
