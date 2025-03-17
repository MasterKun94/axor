package io.masterkun.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.serializers.RecordSerializer;
import com.typesafe.config.ConfigFactory;
import io.masterkun.axor.runtime.SerdeRegistry;
import org.junit.Assert;
import org.junit.Test;

public class KryoSerdeFactoryProviderTest {

    @Test
    public void test() {
        SerdeRegistry registry = new SerdeRegistry(ConfigFactory.load());
        KryoSerdeFactory factory = registry.getFactory(KryoSerdeFactory.class);
        Kryo kryo = factory.getKryoInstance().getKryo();
        Registration registration = kryo.getRegistration(10001);
        Assert.assertTrue(registration.getSerializer() instanceof RecordSerializer<?>);
        Assert.assertEquals(TestRecord.class, registration.getType());
        registration = kryo.getRegistration(10002);
        Assert.assertTrue(registration.getSerializer() instanceof RecordSerializer<?>);
        Assert.assertEquals(TestRecord2.class, registration.getType());
    }

}
