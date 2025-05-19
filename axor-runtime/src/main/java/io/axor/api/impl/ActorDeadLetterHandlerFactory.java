package io.axor.api.impl;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.api.DeadLetter;
import io.axor.api.Pubsub;
import io.axor.exception.ActorException;
import io.axor.exception.ActorRuntimeException;
import io.axor.runtime.DeadLetterHandler;
import io.axor.runtime.DeadLetterHandlerFactory;
import io.axor.runtime.StreamDefinition;

public class ActorDeadLetterHandlerFactory implements DeadLetterHandlerFactory {

    private ActorSystem actorSystem;

    public ActorSystem init(ActorSystem actorSystem) {
        if (this.actorSystem != null) {
            throw new IllegalStateException("Actor system already set");
        }
        this.actorSystem = actorSystem;
        return actorSystem;
    }

    @Override
    public DeadLetterHandler create(StreamDefinition<?> remoteDef, StreamDefinition<?> selfDef) {
        Pubsub<DeadLetter> pubsub = ((ActorSystemImpl) actorSystem).deadLetters();
        ActorRef<?> sender;
        ActorAddress receiver = ActorAddress.create(selfDef.address());
        try {
            sender = actorSystem.get(ActorAddress.create(remoteDef.address()),
                    remoteDef.serde().getType());
        } catch (ActorException e) {
            throw new ActorRuntimeException(e);
        }
        return msg -> pubsub.publishToAll(new DeadLetter(receiver, sender, msg), sender);
    }
}
