package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.DeadLetter;
import io.masterkun.axor.api.Pubsub;
import io.masterkun.axor.exception.ActorException;
import io.masterkun.axor.exception.ActorRuntimeException;
import io.masterkun.axor.runtime.DeadLetterHandler;
import io.masterkun.axor.runtime.DeadLetterHandlerFactory;
import io.masterkun.axor.runtime.StreamDefinition;

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
