package io.masterkun.axor.runtime.serde.kryo;

import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.StreamAddress;
import io.masterkun.axor.runtime.impl.BuiltinSerde;
import io.masterkun.axor.testkit.SerdeTestKit;
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
}
