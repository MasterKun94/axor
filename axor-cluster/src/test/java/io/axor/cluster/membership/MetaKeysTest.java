package io.axor.cluster.membership;

import com.google.protobuf.Any;
import io.axor.cluster.membership.MetaKeys.BooleanMetaKey;
import io.axor.cluster.membership.MetaKeys.ByteMetaKey;
import io.axor.cluster.membership.MetaKeys.DoubleMetaKey;
import io.axor.cluster.membership.MetaKeys.EnumMetaKey;
import io.axor.cluster.membership.MetaKeys.FloatMetaKey;
import io.axor.cluster.membership.MetaKeys.IntMetaKey;
import io.axor.cluster.membership.MetaKeys.LongMetaKey;
import io.axor.cluster.membership.MetaKeys.ProtobufMetaKey;
import io.axor.cluster.membership.MetaKeys.ShortMetaKey;
import io.axor.cluster.membership.MetaKeys.StringListMetaKey;
import io.axor.cluster.membership.MetaKeys.StringMetaKey;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetaKeysTest {

    @Test
    public void testCreateBooleanMetaKey() {
        BooleanMetaKey key = MetaKeys.create(1, "testName", "testDescription", true);
        assertEquals(1, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertTrue(key.get(MetaInfo.EMPTY));
    }

    @Test
    public void testCreateByteMetaKey() {
        ByteMetaKey key = MetaKeys.create(2, "testName", "testDescription", (byte) 10);
        assertEquals(2, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals((byte) 10, key.get(MetaInfo.EMPTY).byteValue());
    }

    @Test
    public void testCreateShortMetaKey() {
        ShortMetaKey key = MetaKeys.create(3, "testName", "testDescription", (short) 100);
        assertEquals(3, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals((short) 100, key.get(MetaInfo.EMPTY).shortValue());
    }

    @Test
    public void testCreateIntMetaKey() {
        IntMetaKey key = MetaKeys.create(4, "testName", "testDescription", 1000);
        assertEquals(4, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals(1000, key.get(MetaInfo.EMPTY).intValue());
    }

    @Test
    public void testCreateLongMetaKey() {
        LongMetaKey key = MetaKeys.create(5, "testName", "testDescription", 10000L);
        assertEquals(5, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals(10000L, key.get(MetaInfo.EMPTY).longValue());
    }

    @Test
    public void testCreateFloatMetaKey() {
        FloatMetaKey key = MetaKeys.create(6, "testName", "testDescription", 100.0f);
        assertEquals(6, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals(100.0f, key.get(MetaInfo.EMPTY), 0.001f);
    }

    @Test
    public void testCreateDoubleMetaKey() {
        DoubleMetaKey key = MetaKeys.create(7, "testName", "testDescription", 1000.0);
        assertEquals(7, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals(1000.0, key.get(MetaInfo.EMPTY), 0.001);
    }

    @Test
    public void testCreateStringMetaKey() {
        StringMetaKey key = MetaKeys.create(8, "testName", "testDescription", "defaultValue");
        assertEquals(8, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals("defaultValue", key.get(MetaInfo.EMPTY));
    }

    @Test
    public void testCreateStringListMetaKey() {
        List<String> defaultValue = new ArrayList<>();
        defaultValue.add("item1");
        defaultValue.add("item2");
        StringListMetaKey key = MetaKeys.create(9, "testName", "testDescription", defaultValue);
        assertEquals(9, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals(defaultValue, key.get(MetaInfo.EMPTY));
    }

    @Test
    public void testCreateEnumMetaKey() {
        TestEnum defaultValue = TestEnum.VALUE1;
        EnumMetaKey<TestEnum> key = MetaKeys.create(10, "testName", "testDescription",
                defaultValue);
        assertEquals(10, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals(defaultValue, key.get(MetaInfo.EMPTY));
    }

    @Test
    public void testCreateProtobufMetaKey() {
        Any defaultValue = Any.newBuilder().setTypeUrl("value1").build();
        ProtobufMetaKey<Any> key = MetaKeys.create(11, "testName", "testDescription", defaultValue);
        assertEquals(11, key.id());
        assertEquals("testName", key.name());
        assertEquals("testDescription", key.description());
        assertEquals(defaultValue, key.get(MetaInfo.EMPTY));
    }

    enum TestEnum {
        VALUE1, VALUE2
    }

}
