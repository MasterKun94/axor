package io.axor.runtime;

import org.junit.Assert;
import org.junit.Test;

public class EventContextSerdeTest {
    @Test
    public void testSerde() throws Exception {
        EventContext.Key<String> key = new EventContext.Key<>(1, "testKey", "description",
                new StringKeyMarshaller());
        EventContext ctx = EventContext.INITIAL.with(key, "Hello");
        Serde<EventContext> serde = new EventContextSerde();
        EventContext deserialize = serde.deserialize(serde.serialize(ctx));
        Assert.assertEquals(ctx, deserialize);
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
