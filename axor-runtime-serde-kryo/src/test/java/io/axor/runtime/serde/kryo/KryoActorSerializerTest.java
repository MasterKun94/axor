package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.exception.ActorException;
import io.axor.exception.ActorRuntimeException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.MsgType;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KryoActorSerializerTest {

    @Test
    public void testReadWithValidInput() throws Exception {
        ActorSystem system = mock(ActorSystem.class);
        Serializer<ActorAddress> addressSerializer = mock(Serializer.class);
        Serializer<MsgType> msgTypeSerializer = mock(Serializer.class);
        Kryo kryo = new Kryo();
        Input input = new Input(new byte[0]);
        KryoActorSerializer serializer = new KryoActorSerializer(system, addressSerializer,
                msgTypeSerializer);

        ActorAddress address = mock(ActorAddress.class);
        MsgType msgType = MsgType.of(String.class);
        ActorRef actorRef = ActorRef.noSender();

        when(addressSerializer.read(kryo, input, ActorAddress.class)).thenReturn(address);
        when(msgTypeSerializer.read(kryo, input, MsgType.class)).thenReturn(msgType);
        when(system.get(address, msgType)).thenReturn(actorRef);

        ActorRef result = serializer.read(kryo, input, ActorRef.class);

        assertEquals(actorRef, result);
        verify(addressSerializer).read(kryo, input, ActorAddress.class);
        verify(msgTypeSerializer).read(kryo, input, MsgType.class);
        verify(system).get(address, msgType);
    }

    @Test
    public void testReadWithActorException() throws Exception {
        ActorSystem system = mock(ActorSystem.class);
        Serializer<ActorAddress> addressSerializer = mock(Serializer.class);
        Serializer<MsgType> msgTypeSerializer = mock(Serializer.class);
        Kryo kryo = new Kryo();
        Input input = new Input(new byte[0]);
        KryoActorSerializer serializer = new KryoActorSerializer(system, addressSerializer,
                msgTypeSerializer);

        ActorAddress address = mock(ActorAddress.class);
        MsgType msgType = MsgType.of(String.class);

        when(addressSerializer.read(kryo, input, ActorAddress.class)).thenReturn(address);
        when(msgTypeSerializer.read(kryo, input, MsgType.class)).thenReturn(msgType);
        when(system.get(address, msgType)).thenThrow(new IllegalMsgTypeException(MsgType.of(Map.class), msgType));

        try {
            serializer.read(kryo, input, ActorRef.class);
            fail("Expected ActorRuntimeException to be thrown");
        } catch (ActorRuntimeException e) {
            assertTrue(e.getCause() instanceof ActorException);
            assertEquals("expected Map but got String", e.getCause().getMessage());
        }
    }
}
