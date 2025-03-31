package io.axor.runtime.stream.grpc;

import io.axor.runtime.EventContext;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.impl.BuiltinSerde;
import io.axor.testkit.SerdeTestKit;
import org.junit.Test;

public class ContextMsgSerdeTest {
    @Test
    public void testSerde() throws Exception {
        Serde<TestElem> s = SerdeRegistry.defaultInstance().create("kryo",
                MsgType.of(TestElem.class));
        Serde<ContextMsg<TestElem>> serde = new ContextMsgSerde<>(s);
        EventContext.Key<String> key1 = new EventContext.Key<>(1, "testKey", "description",
                new StringKeyMarshaller());
        EventContext.Key<String> key2 = new EventContext.Key<>(2, "testKey2", "description",
                new StringKeyMarshaller());
        EventContext ctx = EventContext.INITIAL.with(key1, "Hello")
                .with(key2, "World");

        SerdeTestKit.of(serde)
                .test(new ContextMsg<>(ctx, new TestElem(123, "Test Test")))
                .instanceOf(BuiltinSerde.class);
    }


    public record TestElem(int id, String name) {
    }

    private static class StringKeyMarshaller implements EventContext.KeyMarshaller<String> {
        @Override
        public byte[] write(String value) {
            return value.getBytes();
        }

        @Override
        public String read(byte[] b, int off, int len) {
            return new String(b, off, len);
        }
    }
}
