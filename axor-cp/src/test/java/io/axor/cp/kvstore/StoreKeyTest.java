package io.axor.cp.kvstore;

import com.google.protobuf.ByteString;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class StoreKeyTest {

    @Test
    public void tesNameKey() {
        StoreKey key = new StoreKey.HeadKey(123);
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        ByteBuffer ret = StoreKey.keyToBytes(key, buffer);
        assertEquals(key, StoreKey.bytesToKey(ret));
        assertEquals(key, StoreKey.bytesToKey(ByteBuffer.wrap(key.toByteArray())));
    }

    @Test
    public void tesChildKey() {
        StoreKey key = new StoreKey.ChildKey(123, ByteString.copyFromUtf8("hello"));
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        ByteBuffer ret = StoreKey.keyToBytes(key, buffer);
        assertEquals(key, StoreKey.bytesToKey(ret));
        assertEquals(key, StoreKey.bytesToKey(ByteBuffer.wrap(key.toByteArray())));
    }

    @Test
    public void tesDataKey() {
        StoreKey key = new StoreKey.DataKey(123);
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        ByteBuffer ret = StoreKey.keyToBytes(key, buffer);
        assertEquals(key, StoreKey.bytesToKey(ret));
        assertEquals(key, StoreKey.bytesToKey(ByteBuffer.wrap(key.toByteArray())));
    }
}
