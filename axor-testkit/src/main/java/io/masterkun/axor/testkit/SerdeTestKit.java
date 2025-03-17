package io.masterkun.axor.testkit;

import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.SerdeFactory;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class SerdeTestKit<T> {
    private final Serde<T> serde;

    public SerdeTestKit(Serde<T> serde) {
        this.serde = serde;
    }

    public static <T> SerdeTestKit<T> of(Serde<T> serde) {
        return new SerdeTestKit<>(serde);
    }

    public static <T> SerdeTestKit<T> of(SerdeFactory factory, MsgType<T> msgType) {
        Assert.assertTrue(factory.support(msgType));
        return of(factory.create(msgType));
    }

    public SerdeTestKit<T> impl(String impl) {
        Assert.assertEquals(impl, serde.getImpl());
        return this;
    }

    public SerdeTestKit<T> msgType(MsgType<?> msgType) {
        Assert.assertEquals(msgType, serde.getType());
        return this;
    }

    public SerdeTestKit<T> instanceOf(Class<?> serdeClass) {
        Assert.assertTrue(serdeClass.isAssignableFrom(serde.getClass()));
        return this;
    }

    public SerdeTestKit<T> test(T testObject) throws Exception {
        InputStream serialize = serde.serialize(testObject);
        byte[] bytes = IOUtils.toByteArray(serialize);
        T deserialize = serde.deserialize(new ByteArrayInputStream(bytes));
        Assert.assertEquals(testObject, deserialize);
        Assert.assertEquals(testObject, serde.deserialize(serde.serialize(testObject)));

        if (serialize instanceof Serde.KnownLength) {
            int available = ((Serde.KnownLength) serde.serialize(testObject)).available();
            Assert.assertEquals(available, bytes.length);
        }
        if (serialize instanceof Serde.Drainable) {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ((Serde.Drainable) serde.serialize(testObject)).drainTo(bout);
            Assert.assertArrayEquals(bytes, bout.toByteArray());
        }
        return this;
    }
}
