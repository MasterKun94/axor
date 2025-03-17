package io.masterkun.kactor.runtime.serde.kryo;

import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.runtime.StreamAddress;
import io.masterkun.kactor.runtime.impl.BuiltinSerde;
import io.masterkun.kactor.testkit.SerdeTestKit;
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
