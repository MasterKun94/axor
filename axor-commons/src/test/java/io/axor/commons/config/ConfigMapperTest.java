package io.axor.commons.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigMemorySize;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConfigMapperTest {

    @Test
    public void test() {
        String s = """
                a1=1
                a2=2
                a3=3
                a4=4
                a5=5.0
                a6=true
                a7=7k
                a8=8h
                a9=A
                a11=[11,12]
                a12=[12,13]
                a13=[13,14]
                a14=[14,15]
                a15=[15,16.0]
                a16=[true,false]
                a17=[17M,0]
                a18=[18m,19s]
                a19=[B,C]
                key1=value
                key2=[A,B]
                some=else
                """;
        Config config = ConfigFactory.parseString(s);
        TestRecord test = ConfigMapper.map(config, TestRecord.class);
        assertEquals("1", test.a1);
        assertEquals(2, test.a2);
        assertEquals(3, test.a3);
        assertEquals(4, test.a4, 0);
        assertEquals(5, test.a5, 0);
        assertTrue(test.a6);
        assertEquals(7 * 1024, test.a7.toBytes());
        assertEquals(Duration.ofHours(8), test.a8);
        assertEquals(TestEnum.A, test.a9);
        assertArrayEquals(new String[]{"11", "12"}, test.a11.toArray());
        assertArrayEquals(new Integer[]{12, 13}, test.a12.toArray());
        assertArrayEquals(new Long[]{13L, 14L}, test.a13.toArray());
        assertArrayEquals(new Double[]{14.0, 15.0}, test.a14.toArray());
        assertArrayEquals(new Float[]{15.0f, 16.0f}, test.a15.toArray());
        assertArrayEquals(new Boolean[]{true, false}, test.a16.toArray());
        assertArrayEquals(new ConfigMemorySize[]{ConfigMemorySize.ofBytes(17 * 1024 * 1024),
                ConfigMemorySize.ofBytes(0)}, test.a17.toArray());
        assertArrayEquals(new Duration[]{Duration.ofMinutes(18), Duration.ofSeconds(19)},
                test.a18.toArray());
        assertArrayEquals(new TestEnum[]{TestEnum.B, TestEnum.C}, test.a19.toArray());
        assertNull(test.testNull);
        assertEquals("value", test.testElse);
        assertArrayEquals(new TestEnum[]{TestEnum.A, TestEnum.B}, test.testElse2.toArray());
        assertEquals(config, test.config2);
        assertEquals(1, test.config.entrySet().size());
    }

    @Test
    public void testTest() {
        String s = """
                field = hello
                record2 = {
                  value = world
                  value2 = 2
                  record3 = {
                    a = 3
                  }
                }
                """;
        TestRecord1 record1 = ConfigMapper.map(ConfigFactory.parseString(s), TestRecord1.class);
        assertEquals("hello", record1.field);
        assertEquals("world", record1.record2.value);
        assertEquals(2, record1.record2.value2);
        assertEquals(3, record1.record2.record3.a);
    }

    @Test(expected = ConfigParseException.class)
    public void testThrow() {
        String s = """
                field = hello
                record2 = {
                  value = world
                  value2 = 2
                  record3 = {
                    a = 3aa
                  }
                }
                """;
        try {
            ConfigMapper.map(ConfigFactory.parseString(s), TestRecord1.class);
        } catch (ConfigParseException e) {
            System.out.println(e);
            assertEquals("record2.record3.a", e.getPath());
            // do
            throw e;
        }
    }

    public enum TestEnum {
        A, B, C;
    }

    public record TestRecord(
            @ConfigField("a1") String a1,
            @ConfigField() int a2,
            @ConfigField("a3") long a3,
            @ConfigField("a4") double a4,
            @ConfigField("a5") float a5,
            @ConfigField("a6") boolean a6,
            @ConfigField("a7") ConfigMemorySize a7,
            @ConfigField("a8") Duration a8,
            @ConfigField("a9") TestEnum a9,
            @ConfigField(typeArges = String.class) List<String> a11,
            @ConfigField(typeArges = Integer.class) List<Integer> a12,
            @ConfigField(typeArges = Long.class) List<Long> a13,
            @ConfigField(typeArges = Double.class) List<Double> a14,
            @ConfigField(typeArges = Float.class) List<Float> a15,
            @ConfigField(typeArges = Boolean.class) List<Boolean> a16,
            @ConfigField(typeArges = ConfigMemorySize.class) List<ConfigMemorySize> a17,
            @ConfigField(typeArges = Duration.class) List<Duration> a18,
            @ConfigField(typeArges = TestEnum.class) List<TestEnum> a19,
            @ConfigField(typeArges = String.class, nullable = true) String testNull,
            @ConfigField(value = "key1", typeArges = String.class, nullable = true) String testElse,
            @ConfigField(value = "key2", typeArges = TestEnum.class, nullable = true) List<TestEnum> testElse2,
            @ConfigOrigin Config config,
            @ConfigOrigin(retainAll = true) Config config2
    ) {

    }

    public record TestRecord1(TestRecord2 record2, @ConfigField("field") String field) {
    }

    public record TestRecord2(String value, int value2, TestRecord3 record3) {
    }

    public record TestRecord3(@ConfigField("a") long a) {
    }

}
