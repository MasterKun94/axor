package io.axor.runtime.impl;

import com.google.protobuf.Timestamp;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.serde.kryo.KryoSerdeFactory;
import io.axor.testkit.SerdeTestKit;
import org.junit.Test;

public class AutoTypeSerdeTest {

    @Test
    public void test() throws Exception {
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        registry.getFactory(KryoSerdeFactory.class)
                .addInitializer(kryo -> kryo.register(TestRecord.class, 10211));
        Serde<Object> serde = new AutoTypeSerde<>(registry, MsgType.of(Object.class));
        SerdeTestKit.of(serde)
                .test("Hello")
                .test(123)
                .test(new TestRecord("Hello"))
                .test(Timestamp.newBuilder().setSeconds(123).setNanos(122).build())
                .instanceOf(BuiltinSerde.class)
                .impl("builtin")
                .msgType(MsgType.of(Object.class));
    }

    public record TestRecord(String msg) {
    }
}
