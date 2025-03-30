package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.impl.BuiltinSerde;
import io.axor.testkit.SerdeTestKit;
import org.junit.Test;

public class SerializerAdaptorTest {

    @Test
    public void test() throws Exception {
        var builtin = SerdeRegistry.defaultInstance().create(MsgType.of(StreamAddress.class));
        var serializer = new SerializerAdaptor<>((BuiltinSerde<?>) builtin);
        var factory = SerdeRegistry.defaultInstance().findFactory(KryoSerdeFactory.class).get();
        factory.addInitializer(kryo -> kryo.register(StreamAddress.class, serializer));
        var serde = factory.create(MsgType.of(StreamAddress.class));
        SerdeTestKit.of(serde)
                .impl("kryo")
                .msgType(MsgType.of(StreamAddress.class))
                .instanceOf(KryoSerde.class)
                .test(new StreamAddress("test", 123, "system", "service"));
    }

    @Test
    public void testReadValidStreamAddress() throws Exception {
        var builtin = SerdeRegistry.defaultInstance().create(MsgType.of(StreamAddress.class));
        var serializer = new SerializerAdaptor<>((BuiltinSerde<StreamAddress>) builtin);
        var kryo = new Kryo();
        kryo.register(StreamAddress.class, serializer);

        var output = new Output(1024, -1);
        var streamAddress = new StreamAddress("test", 123, "system", "service");
        serializer.write(kryo, output, streamAddress);

        var input = new Input(output.toBytes());
        var deserialized = serializer.read(kryo, input, StreamAddress.class);
        assert deserialized.equals(streamAddress);
    }

    @Test(expected = RuntimeException.class)
    public void testReadInvalidInput() throws Exception {
        var builtin = SerdeRegistry.defaultInstance().create(MsgType.of(StreamAddress.class));
        var serializer = new SerializerAdaptor<>((BuiltinSerde<StreamAddress>) builtin);
        var kryo = new Kryo();
        kryo.register(StreamAddress.class, serializer);

        var input = new Input(new byte[0]);
        serializer.read(kryo, input, StreamAddress.class);
    }
}
