package io.masterkun.axor.runtime.impl;

import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.StreamAddress;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.TypeReference;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class BuiltinSerdeTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void testSerde() throws Exception {
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        Serde msgTypeSerde = registry.create(MsgType.of(MsgType.class));
        testSerde(msgTypeSerde, MsgType.of(Map.class));
        testSerde(msgTypeSerde, MsgType.of(new TypeReference<Map<String, Integer>>() {
        }));
        testSerde(registry.create(MsgType.of(Serde.class)), msgTypeSerde);
        StreamAddress address = new StreamAddress("localhost", 123, "sys", "name");
        testSerde(registry.create(MsgType.of(StreamAddress.class)), address);
        testSerde(registry.create(MsgType.of(StreamDefinition.class)), new StreamDefinition(address, msgTypeSerde));
    }

    private <T> void testSerde(Serde<T> serde, T obj) throws Exception {
        Assert.assertTrue(serde instanceof BuiltinSerde);
        Assert.assertEquals(obj, serde.deserialize(serde.serialize(obj)));
    }
}
