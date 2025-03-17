package io.masterkun.kactor.runtime;

import io.masterkun.kactor.commons.collection.IntObjectMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MsgTypeTest {

    @Test
    public void testCreate() {
        var type = MsgType.of(String.class);
        assertEquals(String.class, type.type());
        assertTrue(type.typeArgs().isEmpty());

        var type2 = MsgType.of(new TypeReference<Map<String, String>>() {
        });
        assertEquals(Map.class, type2.type());
        assertEquals(2, type2.typeArgs().size());
    }

    @Test
    public void testParse() {
        var type = MsgType.of(new TypeReference<Map<Map.Entry<String, Boolean>, List<IntObjectMap<Integer>>>>() {
        });
        System.out.println(type.qualifiedName());
        System.out.println(type.name());
        System.out.println(type);
        Assert.assertEquals(type, MsgType.parse(type.qualifiedName()));
        var type2 = MsgType.of(String.class);
        Assert.assertEquals(type2, MsgType.parse(type2.qualifiedName()));
    }
}
