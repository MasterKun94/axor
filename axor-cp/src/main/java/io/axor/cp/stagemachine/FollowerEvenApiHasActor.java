package io.axor.cp.stagemachine;

import io.axor.api.impl.ActorUnsafe;
import io.axor.cp.HasActor;
import io.axor.cp.stagemachine.EventApiSignal.FollowerEventApiSignal.ExtendedNoLeader;
import io.axor.cp.stagemachine.EventApiSignal.FollowerEventApiSignal.InstallSnapshotFromLeader;
import org.apache.ratis.proto.RaftProtos.RoleInfoProto;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.statemachine.StateMachine;

import java.util.concurrent.CompletableFuture;

public interface FollowerEvenApiHasActor extends StateMachine.FollowerEventApi, HasActor {
    @Override
    default void notifyExtendedNoLeader(RoleInfoProto roleInfoProto) {
        ActorUnsafe.signal(actor(), new ExtendedNoLeader(roleInfoProto));
    }

    @Override
    default CompletableFuture<TermIndex> notifyInstallSnapshotFromLeader(RoleInfoProto roleInfoProto,
                                                                         TermIndex firstTermIndexInLog) {
        ActorUnsafe.signal(actor(), new InstallSnapshotFromLeader(roleInfoProto,
                firstTermIndexInLog));
        // TODO
        return CompletableFuture.completedFuture(null);
    }
}
