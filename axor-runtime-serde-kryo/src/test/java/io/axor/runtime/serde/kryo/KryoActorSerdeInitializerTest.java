package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.ActorSystem;
import io.axor.api.impl.AbstractActorRef;
import io.axor.api.impl.NoSenderActorRef;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.impl.BuiltinSerdeFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KryoActorSerdeInitializerTest {

    @Test
    public void testInitializeRegistersActorAddressSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(ActorAddress.class).getId(), 1001);
    }

    @Test
    public void testInitializeRegistersActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(ActorRef.class).getId(), 1002);
    }

    @Test
    public void testInitializeRegistersActorRefRichSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(ActorRefRich.class).getId(), 1003);
    }

    @Test
    public void testInitializeRegistersAbstractActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(AbstractActorRef.class).getId(), 1004);
    }

    @Test
    public void testInitializeRegistersLocalActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(NoSenderActorRef.class).getId(), 1007);
    }

    @Test
    public void testInitializeRegistersRemoteActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(NoSenderActorRef.class).getId(), 1007);
    }

    @Test
    public void testInitializeRegistersNoSenderActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(NoSenderActorRef.class).getId(), 1007);
    }

    @Test
    public void testInitializeRegistersMsgTypeSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(MsgType.class).getId(), 1008);
    }

    @Test
    public void testInitializeRegistersSerdeSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(Serde.class).getId(), 1009);
    }

    @Test
    public void testInitializeRegistersStreamDefinitionSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(StreamDefinition.class).getId(), 1010);
    }

    @Test
    public void testInitializeRegistersStreamAddressSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        KryoSerdeFactory serdeFactory = mock(KryoSerdeFactory.class);
        SerdeRegistry registry = mock(SerdeRegistry.class);
        BuiltinSerdeFactory builtin = mock(BuiltinSerdeFactory.class);

        when(registry.getFactory(BuiltinSerdeFactory.class)).thenReturn(builtin);

        Kryo kryo = new Kryo();
        doAnswer(invocation -> {
            ((KryoInitializer) invocation.getArgument(0)).initialize(kryo);
            return null;
        }).when(serdeFactory).addInitializer(any());

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(kryo.getClassResolver().getRegistration(StreamAddress.class).getId(), 1011);
    }
}
