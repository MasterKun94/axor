package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Serializer;
import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.ActorSystem;
import io.axor.api.ActorSystemSerdeInitializer;
import io.axor.api.impl.AbstractActorRef;
import io.axor.api.impl.NoSenderActorRef;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.impl.BuiltinSerdeFactory;

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
            try {
                kryo.register(Class.forName("io.axor.api.impl.LocalActorRef"),
                        actorSerializer, 1005);
                kryo.register(Class.forName("io.axor.api.impl.RemoteActorRef"),
                        actorSerializer, 1006);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
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
