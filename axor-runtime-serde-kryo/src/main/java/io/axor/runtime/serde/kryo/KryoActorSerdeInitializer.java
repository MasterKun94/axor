package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Serializer;
import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.ActorSystem;
import io.axor.api.ActorSystemSerdeInitializer;
import io.axor.api.Eventbus;
import io.axor.api.Pubsub;
import io.axor.api.ReliableDelivery;
import io.axor.api.SystemEvent;
import io.axor.api.impl.AbstractActorRef;
import io.axor.api.impl.NoSenderActorRef;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.impl.BuiltinSerdeFactory;

public class KryoActorSerdeInitializer extends ActorSystemSerdeInitializer<KryoSerdeFactory> {
    public static final int ID_BASE = 1000;

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
            kryo.register(ActorAddress.class, addressSerializer, ID_BASE + 1);
            kryo.register(ActorRef.class, actorSerializer, ID_BASE + 2);
            kryo.register(ActorRefRich.class, actorSerializer, ID_BASE + 3);
            kryo.register(AbstractActorRef.class, actorSerializer, ID_BASE + 4);
            try {
                kryo.register(Class.forName("io.axor.api.impl.LocalActorRef"),
                        actorSerializer, ID_BASE + 5);
                kryo.register(Class.forName("io.axor.api.impl.RemoteActorRef"),
                        actorSerializer, ID_BASE + 6);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            kryo.register(NoSenderActorRef.class, actorSerializer, ID_BASE + 7);
            kryo.register(MsgType.class, msgTypeSerializer, ID_BASE + 8);
            kryo.register(Serde.class, serdeSerializer, ID_BASE + 9);
            kryo.register(StreamDefinition.class, streamDefinitionSerializer, ID_BASE + 10);
            kryo.register(StreamAddress.class, streamAddressSerializer, ID_BASE + 11);
            kryo.register(SystemEvent.ActorAction.class, ID_BASE + 12);
            kryo.register(SystemEvent.ActorEvent.class, ID_BASE + 13);
            kryo.register(SystemEvent.ActorStarted.class, ID_BASE + 14);
            kryo.register(SystemEvent.ActorStopped.class, ID_BASE + 15);
            kryo.register(SystemEvent.ActorRestarted.class, ID_BASE + 16);
            kryo.register(SystemEvent.ActorError.class, ID_BASE + 17);
            kryo.register(SystemEvent.StreamEvent.class, ID_BASE + 18);
            kryo.register(SystemEvent.StreamOutOpened.class, ID_BASE + 19);
            kryo.register(SystemEvent.StreamOutClosed.class, ID_BASE + 20);
            kryo.register(SystemEvent.StreamInOpened.class, ID_BASE + 21);
            kryo.register(SystemEvent.StreamInClosed.class, ID_BASE + 22);
            kryo.register(Status.class, ID_BASE + 23);
            kryo.register(StatusCode.class, ID_BASE + 24);
            kryo.register(ReliableDelivery.MsgAckSuccess.class, ID_BASE + 25);

            kryo.register(Eventbus.Subscribe.class, ID_BASE + 31);
            kryo.register(Eventbus.Unsubscribe.class, ID_BASE + 32);
            kryo.register(Eventbus.SubscribeSuccess.class, ID_BASE + 33);
            kryo.register(Eventbus.SubscribeFailed.class, ID_BASE + 34);
            kryo.register(Eventbus.UnsubscribeSuccess.class, ID_BASE + 35);
            kryo.register(Eventbus.UnsubscribeFailed.class, ID_BASE + 36);
            kryo.register(Pubsub.PublishToAll.class, ID_BASE + 37);
            kryo.register(Pubsub.SendToOne.class, ID_BASE + 38);
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
