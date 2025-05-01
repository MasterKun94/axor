package io.axor.cp.stagemachine;

import io.axor.api.impl.ActorUnsafe;
import io.axor.cp.HasActor;
import io.axor.cp.stagemachine.EventApiSignal.LeaderEventApiSignal.FollowerSlowness;
import io.axor.cp.stagemachine.EventApiSignal.LeaderEventApiSignal.LeaderReady;
import io.axor.cp.stagemachine.EventApiSignal.LeaderEventApiSignal.NotLeader;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.statemachine.StateMachine;
import org.apache.ratis.statemachine.TransactionContext;

import java.util.Collection;

public interface LeaderEventApiHasActor extends StateMachine.LeaderEventApi, HasActor {
    @Override
    default void notifyFollowerSlowness(RaftProtos.RoleInfoProto leaderInfo,
                                        RaftPeer slowFollower) {
        ActorUnsafe.signal(actor(), new FollowerSlowness(leaderInfo, slowFollower));
    }

    @Override
    default void notifyNotLeader(Collection<TransactionContext> pendingEntries) {
        // TODO
        ActorUnsafe.signal(actor(), new NotLeader(pendingEntries));
    }

    @Override
    default void notifyLeaderReady() {
        ActorUnsafe.signal(actor(), new LeaderReady());
    }
}
