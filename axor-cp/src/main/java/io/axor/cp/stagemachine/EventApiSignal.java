package io.axor.cp.stagemachine;

import org.apache.ratis.proto.RaftProtos.InstallSnapshotResult;
import org.apache.ratis.proto.RaftProtos.LogEntryProto;
import org.apache.ratis.proto.RaftProtos.RaftConfigurationProto;
import org.apache.ratis.proto.RaftProtos.RoleInfoProto;
import org.apache.ratis.protocol.RaftGroupMemberId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.statemachine.TransactionContext;

import java.util.Collection;

public sealed interface EventApiSignal extends StageMachineSignal {
    sealed interface LeaderEventApiSignal extends EventApiSignal {
        record FollowerSlowness(RoleInfoProto leaderInfo,
                                RaftPeer slowFollower) implements LeaderEventApiSignal {
        }

        record NotLeader(
                Collection<TransactionContext> pendingEntries) implements LeaderEventApiSignal {
        }

        record LeaderReady() implements LeaderEventApiSignal {
        }
    }

    sealed interface FollowerEventApiSignal extends EventApiSignal {
        record ExtendedNoLeader(RoleInfoProto roleInfoProto) implements FollowerEventApiSignal {
        }

        record InstallSnapshotFromLeader(RoleInfoProto roleInfoProto,
                                         TermIndex firstTermIndexInLog) implements FollowerEventApiSignal {
        }
    }

    record LeaderChanged(RaftGroupMemberId groupMemberId,
                         RaftPeerId newLeaderId) implements EventApiSignal {
    }

    record TermIndexUpdated(long term, long index) implements EventApiSignal {
    }

    record ConfigurationChanged(long term, long index,
                                RaftConfigurationProto newRaftConfiguration) implements EventApiSignal {
    }

    record GroupRemove() implements EventApiSignal {
    }

    record LogFailed(Throwable cause,
                     LogEntryProto failedEntry) implements EventApiSignal {
    }

    record SnapshotInstalled(InstallSnapshotResult result, long snapshotIndex,
                             RaftPeer peer) implements EventApiSignal {
    }

    record ServerShutdown(RoleInfoProto roleInfo,
                          boolean allServer) implements EventApiSignal {
    }
}
