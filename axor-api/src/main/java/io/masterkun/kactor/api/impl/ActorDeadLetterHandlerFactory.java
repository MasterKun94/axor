package io.masterkun.kactor.api.impl;

import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.api.ActorSystem;
import io.masterkun.kactor.api.DeadLetter;
import io.masterkun.kactor.api.Pubsub;
import io.masterkun.kactor.exception.ActorException;
import io.masterkun.kactor.exception.ActorRuntimeException;
import io.masterkun.kactor.runtime.DeadLetterHandler;
import io.masterkun.kactor.runtime.DeadLetterHandlerFactory;
import io.masterkun.kactor.runtime.StreamDefinition;

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
            sender = actorSystem.get(ActorAddress.create(remoteDef.address()), remoteDef.serde().getType());
        } catch (ActorException e) {
            throw new ActorRuntimeException(e);
        }
        return msg -> pubsub.publishToAll(new DeadLetter(receiver, sender, msg), sender);
    }
}
