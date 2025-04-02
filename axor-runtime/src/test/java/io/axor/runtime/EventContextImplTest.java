package io.axor.runtime;

import io.axor.commons.collection.IntObjectHashMap;
import io.axor.commons.collection.IntObjectMap;
import io.axor.runtime.EventContext.Key;
import io.axor.runtime.EventContext.KeyMarshaller;
import io.axor.runtime.impl.DefaultEventDispatcher;
import io.stateeasy.concurrent.Try;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class EventContextImplTest {

    static DefaultEventDispatcher dispatcher;

    @BeforeClass
    public static void init() {
        dispatcher = new DefaultEventDispatcher("test");
    }

    @AfterClass
    public static void tearDown() {
        dispatcher.shutdown();
    }

    @Test
    public void testGetExistingKey() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>();
        map.put(key.id(), new EventContextImpl.BytesHolder("testValue".getBytes()));
        EventContextImpl context = new EventContextImpl(map);
        String result = context.get(key);
        assertEquals("testValue", result);
    }

    @Test
    public void testGetNonExistingKey() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>();
        EventContextImpl context = new EventContextImpl(map);
        String result = context.get(key);
        assertNull(result);
    }

    @Test
    public void testWithExistingKey() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>();
        EventContextImpl context = new EventContextImpl(map);
        EventContext newContext = context.with(key, "testValue");
        String result = newContext.get(key);
        assertEquals("testValue", result);
    }

    @Test
    public void testWithOverwriteExistingKey() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>();
        map.put(key.id(), new EventContextImpl.BytesHolder("oldValue".getBytes()));
        EventContextImpl context = new EventContextImpl(map);
        EventContext newContext = context.with(key, "newValue");
        String result = newContext.get(key);
        assertEquals("newValue", result);
    }

    @Test
    public void testWithNonExistingKey() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        Key<String> newKey = new Key<>(2, "newTestKey", "newDescription",
                new StringKeyMarshaller());
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>();
        map.put(key.id(), new EventContextImpl.BytesHolder("testValue".getBytes()));
        EventContextImpl context = new EventContextImpl(map);
        EventContext newContext = context.with(newKey, "newValue");
        String result = newContext.get(newKey);
        assertEquals("newValue", result);
    }

    @Test
    public void testWithoutExistingKey() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>();
        map.put(key.id(), new EventContextImpl.BytesHolder("testValue".getBytes()));
        EventContextImpl context = new EventContextImpl(map);
        EventContext newContext = context.without(key);
        assertNull(newContext.get(key));
    }

    @Test
    public void testWithoutNonExistingKey() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>();
        EventContextImpl context = new EventContextImpl(map);
        EventContext newContext = context.without(key);
        assertNull(newContext.get(key));
    }

    @Test
    public void testWithoutLastKey() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>();
        map.put(key.id(), new EventContextImpl.BytesHolder("testValue".getBytes()));
        EventContextImpl context = new EventContextImpl(map);
        EventContext newContext = context.without(key);
        assertSame(EventContext.INITIAL, newContext);
    }

    @Test
    public void testExecute() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        EventContext context = EventContext.INITIAL.with(key, "Hello");
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher("test");
        context.execute(() -> {
            Assert.assertSame(context, EventContext.current());
            Assert.assertEquals("Hello", context.get(key));
        }, dispatcher);
    }

    @Test
    public void testRunAsync() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        EventContext context = EventContext.INITIAL.with(key, "Hello");
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher("test");
        context.runAsync(() -> {
            Assert.assertSame(context, EventContext.current());
            Assert.assertEquals("Hello", context.get(key));
        }, dispatcher).toFuture().syncUninterruptibly();
    }

    @Test
    public void testSupplyAsync() {
        Key<String> key = new Key<>(1, "testKey", "description", new StringKeyMarshaller());
        EventContext context = EventContext.INITIAL.with(key, "Hello");
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher("test");
        var ret = context.supplyAsync(() -> {
            Assert.assertSame(context, EventContext.current());
            return context.get(key);
        }, dispatcher).toFuture().syncUninterruptibly();
        Assert.assertEquals(Try.success("Hello"), ret);
    }


    private static class StringKeyMarshaller implements KeyMarshaller<String> {
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
