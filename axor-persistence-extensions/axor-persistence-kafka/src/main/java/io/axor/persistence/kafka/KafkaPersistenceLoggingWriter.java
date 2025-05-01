package io.axor.persistence.kafka;

import com.typesafe.config.Config;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.util.ByteArray;
import io.axor.persistence.logging.IdAndOffset;
import io.axor.persistence.logging.PersistenceLoggingWriter;
import io.axor.runtime.Serde;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.util.List;
import java.util.Properties;

public class KafkaPersistenceLoggingWriter<K, V> implements PersistenceLoggingWriter<K, V> {
    private final String topic;
    private final KafkaProducer<K, V> producer;
    private final EventExecutor executor;
    private long id;

    public KafkaPersistenceLoggingWriter(Config config,
                                         Serde<K> keySerde,
                                         Serde<V> valueSerde,
                                         EventExecutor executor) {
        this.executor = executor;
        Properties properties = new Properties();
        for (var entry : config.getConfig("properties").entrySet()) {
            properties.put(entry.getKey(), entry.getValue().unwrapped().toString());
        }
        this.topic = config.getString("topic");

        this.producer = new KafkaProducer<>(properties,
                new KafkaSerializerAdaptor<>(keySerde),
                new KafkaSerializerAdaptor<>(valueSerde));
    }

    @Override
    public EventStage<IdAndOffset> write(K key, V value) {
        EventPromise<IdAndOffset> promise = executor.newPromise();
        write(key, value, promise);
        return promise;
    }

    private void write(K key, V value, EventPromise<IdAndOffset> promise) {

        if (executor.inExecutor()) {
            byte[] bytes = new byte[8];
            long id = this.id++;
            ByteArray.setLong(bytes, 0, id);
            List<Header> headers = List.of(new RecordHeader("INC_ID", bytes));
            var record = new ProducerRecord<>(topic, null, key, value, headers);
            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    promise.success(new IdAndOffset(id, metadata.offset()));
                } else {
                    promise.failure(exception);
                }
            });
        } else {
            executor.execute(() -> write(key, value, promise));
        }
    }
}
