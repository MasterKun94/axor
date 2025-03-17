package io.masterkun.axor.runtime.serde.kryo;

import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.testkit.SerdeTestKit;
import org.junit.Test;

public class KryoAutoTypeSerdeTest {

    @Test
    public void test() throws Exception {
        KryoSerdeFactory factory = new KryoSerdeFactory(4096, 4096 << 2,
                SerdeRegistry.defaultInstance());
        SerdeTestKit.of(factory, MsgType.of(TestElem.class))
                .impl("kryo")
                .msgType(MsgType.of(TestElem.class))
                .instanceOf(KryoSerde.class)
                .test(new TestElem("bbb", 12));
    }

    public record TestElem(String name, int age) {
    }
}
