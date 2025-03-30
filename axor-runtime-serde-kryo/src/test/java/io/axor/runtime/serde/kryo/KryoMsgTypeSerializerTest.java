package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.axor.runtime.MsgType;
import io.axor.runtime.TypeReference;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class KryoMsgTypeSerializerTest {
    private static final Kryo kryo = new Kryo();
    private static final ByteBufferOutput output = new ByteBufferOutput(1024);
    private static final Serializer<Class> clsSerializer =
            kryo.register(Class.class).getSerializer();
    private static final KryoMsgTypeSerializer msgTypeSerializer =
            new KryoMsgTypeSerializer(clsSerializer);


    @Test
    public void test() {
        kryo.setRegistrationRequired(false);
        test(MsgType.of(String.class));
        test(MsgType.of(new TypeReference<Map<String, Integer>>() {
        }));
    }

    private void test(MsgType<?> msgType) {
        msgTypeSerializer.write(kryo, output, msgType);
        MsgType read = msgTypeSerializer.read(kryo, new ByteBufferInput(output.toBytes()),
                MsgType.class);
        assertEquals(msgType, read);
        output.reset();
    }
}
