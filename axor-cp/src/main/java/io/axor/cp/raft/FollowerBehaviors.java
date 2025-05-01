package io.axor.cp.raft;

import io.axor.api.ActorContext;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.impl.ActorUnsafe;
import io.axor.cp.messages.RaftMessage;
import io.axor.runtime.Signal;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FollowerBehaviors {
    private final RaftContext raftContext;
    private final ActorContext<RaftMessage> context;
    private ScheduledFuture<?> heartbeatTimeoutChecker;

    public FollowerBehaviors(RaftContext raftContext, ActorContext<RaftMessage> context) {
        this.raftContext = raftContext;
        this.context = context;
        long interval = raftContext.config().leaderHeartbeatInterval().toMillis();
        long timeout = raftContext.config().leaderHeartbeatTimeout().toMillis();
        this.heartbeatTimeoutChecker = context.dispatcher().scheduleWithFixedDelay(() -> {
            if (System.currentTimeMillis() - raftContext.leaderLastHeartbeat() > timeout) {
                ActorUnsafe.signalInline(context.self(), new CandidateSignal());
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    public Behavior<RaftMessage> sync() {

        return Behaviors.receive(m -> {

            return Behaviors.unhandled();
        }, signal -> {
            if (signal instanceof CandidateSignal) {
                return new CandidateBehaviors(raftContext, context).get();
            }
            return Behaviors.unhandled();
        });
    }

    public Behavior<RaftMessage> chase() {
        return Behaviors.receive(m -> {

            return Behaviors.unhandled();
        }, signal -> {
            if (signal instanceof CandidateSignal) {
                return new CandidateBehaviors(raftContext, context).get();
            }
            return Behaviors.unhandled();
        });
    }

    public Behavior<RaftMessage> lag() {
        return Behaviors.receive(m -> {

            return Behaviors.unhandled();
        }, signal -> {
            if (signal instanceof CandidateSignal) {
                return new CandidateBehaviors(raftContext, context).get();
            }
            return Behaviors.unhandled();
        });
    }

    private record CandidateSignal() implements Signal {
    }
}
