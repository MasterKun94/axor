package io.masterkun.kactor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Serializer;
import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.api.ActorRefRich;
import io.masterkun.kactor.api.ActorSystem;
import io.masterkun.kactor.api.ActorSystemSerdeInitializer;
import io.masterkun.kactor.api.impl.AbstractActorRef;
import io.masterkun.kactor.api.impl.LocalActorRef;
import io.masterkun.kactor.api.impl.NoSenderActorRef;
import io.masterkun.kactor.api.impl.RemoteActorRef;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.Serde;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.runtime.StreamAddress;
import io.masterkun.kactor.runtime.StreamDefinition;
import io.masterkun.kactor.runtime.impl.BuiltinSerdeFactory;

public class KryoActorSerdeInitializer extends ActorSystemSerdeInitializer<KryoSerdeFactory> {

    @SuppressWarnings({"unchecked"})
    @Override
    protected void initialize(ActorSystem actorSystem,
                              KryoSerdeFactory serdeFactory,
                              SerdeRegistry registry) {
        BuiltinSerdeFactory builtin = registry.getFactory(BuiltinSerdeFactory.class);
        serdeFactory.addInitializer(kryo -> {
            var addressSerializer = kryo.getDefaultSerializer(ActorAddress.class);
            var msgTypeSerializer = serializerAdaptor(builtin, MsgType.class);
            var serdeSerializer = serializerAdaptor(builtin, Serde.class);
            var streamAddressSerializer = serializerAdaptor(builtin, StreamAddress.class);
            var streamDefinitionSerializer = serializerAdaptor(builtin, StreamDefinition.class);
            var actorSerializer = new KryoActorSerializer(actorSystem,
                    addressSerializer, msgTypeSerializer);
            kryo.register(ActorAddress.class, addressSerializer, 1001);
            kryo.register(ActorRef.class, actorSerializer, 1002);
            kryo.register(ActorRefRich.class, actorSerializer, 1003);
            kryo.register(AbstractActorRef.class, actorSerializer, 1004);
            kryo.register(LocalActorRef.class, actorSerializer, 1005);
            kryo.register(RemoteActorRef.class, actorSerializer, 1006);
            kryo.register(NoSenderActorRef.class, actorSerializer, 1007);
            kryo.register(MsgType.class, msgTypeSerializer, 1008);
            kryo.register(Serde.class, serdeSerializer, 1009);
            kryo.register(StreamDefinition.class, streamDefinitionSerializer, 1010);
            kryo.register(StreamAddress.class, streamAddressSerializer, 1011);
        });
    }

    private <T> Serializer<T> serializerAdaptor(BuiltinSerdeFactory builtin, Class<T> type) {
        return new SerializerAdaptor<>(builtin.create(MsgType.of(type)));
    }

    @Override
    protected Class<KryoSerdeFactory> getFactoryClass() {
        return KryoSerdeFactory.class;
    }

    @Override
    public int priority() {
        return 1;
    }
}
