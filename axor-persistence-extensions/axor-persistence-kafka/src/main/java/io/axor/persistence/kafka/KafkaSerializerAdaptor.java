package io.axor.persistence.kafka;

import io.axor.runtime.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;

public class KafkaSerializerAdaptor<T> implements Serializer<T> {
    private final Serde<T> serde;

    public KafkaSerializerAdaptor(Serde<T> serde) {
        this.serde = serde;
    }

    @Override
    public byte[] serialize(String topic, T data) {
        try (var in = serde.serialize(data)) {
            if (in instanceof Serde.KnownLength length) {
                byte[] bytes = new byte[length.available()];
                in.readNBytes(bytes, 0, bytes.length);
                return bytes;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
