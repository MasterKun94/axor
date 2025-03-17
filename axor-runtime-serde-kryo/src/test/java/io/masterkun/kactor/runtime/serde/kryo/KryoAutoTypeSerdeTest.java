package io.masterkun.kactor.runtime.serde.kryo;

import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.testkit.SerdeTestKit;
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
