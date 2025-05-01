package io.axor.cp.stagemachine;

import io.axor.api.impl.ActorUnsafe;
import io.axor.cp.HasActor;
import io.axor.cp.stagemachine.EventApiSignal.ConfigurationChanged;
import io.axor.cp.stagemachine.EventApiSignal.GroupRemove;
import io.axor.cp.stagemachine.EventApiSignal.LeaderChanged;
import io.axor.cp.stagemachine.EventApiSignal.LogFailed;
import io.axor.cp.stagemachine.EventApiSignal.ServerShutdown;
import io.axor.cp.stagemachine.EventApiSignal.SnapshotInstalled;
import io.axor.cp.stagemachine.EventApiSignal.TermIndexUpdated;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.proto.RaftProtos.InstallSnapshotResult;
import org.apache.ratis.proto.RaftProtos.RaftConfigurationProto;
import org.apache.ratis.proto.RaftProtos.RoleInfoProto;
import org.apache.ratis.protocol.RaftGroupMemberId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.statemachine.StateMachine;

public interface EventApiHasActor extends StateMachine.EventApi, HasActor {
    @Override
    default void notifyLeaderChanged(RaftGroupMemberId groupMemberId, RaftPeerId newLeaderId) {
        ActorUnsafe.signal(actor(), new LeaderChanged(groupMemberId, newLeaderId));
    }

    @Override
    default void notifyTermIndexUpdated(long term, long index) {
        ActorUnsafe.signal(actor(), new TermIndexUpdated(term, index));
    }

    @Override
    default void notifyConfigurationChanged(long term, long index,
                                            RaftConfigurationProto newRaftConfiguration) {
        ActorUnsafe.signal(actor(), new ConfigurationChanged(term, index, newRaftConfiguration));
    }

    @Override
    default void notifyGroupRemove() {
        ActorUnsafe.signal(actor(), new GroupRemove());
    }

    @Override
    default void notifyLogFailed(Throwable cause, RaftProtos.LogEntryProto failedEntry) {
        ActorUnsafe.signal(actor(), new LogFailed(cause, failedEntry));
    }

    @Override
    default void notifySnapshotInstalled(InstallSnapshotResult result, long snapshotIndex,
                                         RaftPeer peer) {
        ActorUnsafe.signal(actor(), new SnapshotInstalled(result, snapshotIndex, peer));
    }

    @Override
    default void notifyServerShutdown(RoleInfoProto roleInfo, boolean allServer) {
        ActorUnsafe.signal(actor(), new ServerShutdown(roleInfo, allServer));
    }
}
