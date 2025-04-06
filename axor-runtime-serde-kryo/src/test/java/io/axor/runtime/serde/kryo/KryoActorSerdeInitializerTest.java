package io.axor.runtime.serde.kryo;

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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class KryoActorSerdeInitializerTest {

    @Test
    public void testInitializeRegistersActorAddressSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1001, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(ActorAddress.class).getId());
    }

    @Test
    public void testInitializeRegistersActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1002, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(ActorRef.class).getId());
    }

    @Test
    public void testInitializeRegistersActorRefRichSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1003, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(ActorRefRich.class).getId());
    }

    @Test
    public void testInitializeRegistersAbstractActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1004, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(AbstractActorRef.class).getId());
    }

    @Test
    public void testInitializeRegistersLocalActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1007, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(NoSenderActorRef.class).getId());
    }

    @Test
    public void testInitializeRegistersRemoteActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1007, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(NoSenderActorRef.class).getId());
    }

    @Test
    public void testInitializeRegistersNoSenderActorRefSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1007, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(NoSenderActorRef.class).getId());
    }

    @Test
    public void testInitializeRegistersMsgTypeSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1008, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(MsgType.class).getId());
    }

    @Test
    public void testInitializeRegistersSerdeSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1009, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(Serde.class).getId());
    }

    @Test
    public void testInitializeRegistersStreamDefinitionSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);

        assertEquals(1010, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(StreamDefinition.class).getId());
    }

    @Test
    public void testInitializeRegistersStreamAddressSerializer() {
        ActorSystem actorSystem = mock(ActorSystem.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        KryoSerdeFactory serdeFactory = registry.getFactory(KryoSerdeFactory.class);

        KryoActorSerdeInitializer initializer = new KryoActorSerdeInitializer();
        initializer.initialize(actorSystem, serdeFactory, registry);
        assertEquals(1011, serdeFactory.getKryoInstance().getKryo()
                .getRegistration(StreamAddress.class).getId());
    }
}
