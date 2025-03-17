package io.masterkun.kactor.cluster.membership;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetaInfoTest {

    @Test
    public void test() {
        var boolOpt = MetaKeys.create(1, "bool_opt", "Boolean Option", true);
        var byteOpt = MetaKeys.create(2, "byte_opt", "Byte Option", (byte) 1);
        var shortOpt = MetaKeys.create(3, "short_opt", "Short Option", (short) 2);
        var intOpt = MetaKeys.create(4, "int_opt", "Integer Option", 3);
        var longOpt = MetaKeys.create(5, "long_opt", "Long Option", 4L);
        var floatOpt = MetaKeys.create(6, "float_opt", "Float Option", 5f);
        var doubleOpt = MetaKeys.create(7, "double_opt", "Double Option", 6d);
        var stringOpt = MetaKeys.create(8, "string_opt", "String Option", "String");
        var enumOpt = MetaKeys.create(9, "enum_opt", "Enum Option", Type.A);
        var objOpt = MetaKeys.create(10, "enum_opt2", "Enum Option", Type.A);

        MetaInfo metaInfo = MetaInfo.EMPTY;
        assertEquals(0, metaInfo.size());
        assertTrue(boolOpt.get(metaInfo));
        assertEquals(1, byteOpt.get(metaInfo).byteValue());
        assertEquals(2, shortOpt.get(metaInfo).shortValue());
        assertEquals(3, intOpt.get(metaInfo).intValue());
        assertEquals(4, longOpt.get(metaInfo).longValue());
        assertEquals(5, floatOpt.get(metaInfo), 0);
        assertEquals(6, doubleOpt.get(metaInfo), 0);
        assertEquals("String", stringOpt.get(metaInfo));
        assertEquals(Type.A, enumOpt.get(metaInfo));
        assertEquals(Type.A, objOpt.get(metaInfo));

        metaInfo = metaInfo.transform(
                boolOpt.upsert(false),
                byteOpt.upsert((byte) 11),
                shortOpt.upsert((short) 12),
                intOpt.upsert(13),
                longOpt.upsert(14L),
                floatOpt.upsert(15f),
                doubleOpt.upsert(16d),
                stringOpt.upsert("test"),
                enumOpt.upsert(Type.B),
                objOpt.upsert(Type.B)
        );
        assertEquals(10, metaInfo.size());
        assertFalse(boolOpt.get(metaInfo));
        assertEquals(11, byteOpt.get(metaInfo).byteValue());
        assertEquals(12, shortOpt.get(metaInfo).shortValue());
        assertEquals(13, intOpt.get(metaInfo).intValue());
        assertEquals(14, longOpt.get(metaInfo).longValue());
        assertEquals(15, floatOpt.get(metaInfo), 0);
        assertEquals(16, doubleOpt.get(metaInfo), 0);
        assertEquals("test", stringOpt.get(metaInfo));
        assertEquals(Type.B, enumOpt.get(metaInfo));
        assertEquals(Type.B, objOpt.get(metaInfo));

        metaInfo = metaInfo.transform(
                boolOpt.delete(),
                byteOpt.delete(),
                shortOpt.delete(),
                intOpt.upsert(23),
                longOpt.upsert(24L),
                floatOpt.upsert(25f),
                doubleOpt.upsert(26d),
                stringOpt.upsert("test2"),
                objOpt.upsert(Type.A)
        );
        assertEquals(7, metaInfo.size());
        assertTrue(boolOpt.get(metaInfo));
        assertEquals(1, byteOpt.get(metaInfo).byteValue());
        assertEquals(2, shortOpt.get(metaInfo).shortValue());
        assertEquals(23, intOpt.get(metaInfo).intValue());
        assertEquals(24, longOpt.get(metaInfo).longValue());
        assertEquals(25, floatOpt.get(metaInfo), 0);
        assertEquals(26, doubleOpt.get(metaInfo), 0);
        assertEquals("test2", stringOpt.get(metaInfo));
        assertEquals(Type.B, enumOpt.get(metaInfo));
        assertEquals(Type.A, objOpt.get(metaInfo));
    }

    public enum Type {
        A, B,
    }

}
