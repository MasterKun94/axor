package io.masterkun.kactor.runtime;

import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.api.ActorSystem;
import io.masterkun.kactor.api.ActorSystemSerdeInitializer;
import io.masterkun.kactor.runtime.impl.BuiltinSerde;
import io.masterkun.kactor.runtime.impl.BuiltinSerdeFactory;

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
