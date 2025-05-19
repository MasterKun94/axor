package io.axor.runtime;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.api.ActorSystemSerdeInitializer;
import io.axor.runtime.impl.BuiltinSerde;
import io.axor.runtime.impl.BuiltinSerdeFactory;

public class BuiltinActorSerdeInitializer extends ActorSystemSerdeInitializer<BuiltinSerdeFactory> {

    @Override
    protected void initialize(ActorSystem actorSystem,
                              BuiltinSerdeFactory serdeFactory,
                              SerdeRegistry serdeRegistry) {
        @SuppressWarnings("rawtypes")
        BuiltinSerde<MsgType> msgTypeSerde = serdeFactory.create(MsgType.of(MsgType.class));
        BuiltinActorAddressSerde actorAddressSerde = new BuiltinActorAddressSerde();
        BuiltinActorSerde actorSerde = new BuiltinActorSerde(
                actorAddressSerde, msgTypeSerde, actorSystem);
        serdeFactory.register(ActorAddress.class, actorAddressSerde);
        serdeFactory.register(ActorRef.class, actorSerde);
    }

    @Override
    protected Class<BuiltinSerdeFactory> getFactoryClass() {
        return BuiltinSerdeFactory.class;
    }
}
