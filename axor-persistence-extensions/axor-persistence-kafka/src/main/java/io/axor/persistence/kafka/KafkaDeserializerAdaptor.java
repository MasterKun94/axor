package io.axor.persistence.kafka;

import io.axor.runtime.Serde;
import io.axor.runtime.SerdeByteArrayInputStream;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;

public class KafkaDeserializerAdaptor<T> implements Deserializer<T> {
    private final Serde<T> serde;

    public KafkaDeserializerAdaptor(Serde<T> serde) {
        this.serde = serde;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        try {
            return serde.deserialize(new SerdeByteArrayInputStream(data));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
