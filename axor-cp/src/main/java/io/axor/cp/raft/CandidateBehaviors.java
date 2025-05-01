package io.axor.cp.raft;

import io.axor.api.ActorContext;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.cp.messages.RaftMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CandidateBehaviors {
    private static final Logger LOG = LoggerFactory.getLogger(CandidateBehaviors.class);

    private final RaftContext raftContext;
    private final ActorContext<RaftMessage> context;

    public CandidateBehaviors(RaftContext raftContext, ActorContext<RaftMessage> context) {
        this.raftContext = raftContext;
        this.context = context;
    }

    public Behavior<RaftMessage> get() {
        return Behaviors.receiveMessage(msg -> {

            return Behaviors.unhandled();
        });
    }
}
