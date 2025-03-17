package io.masterkun.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Serializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import io.masterkun.axor.runtime.SerdeFactoryProvider;
import io.masterkun.axor.runtime.SerdeRegistry;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public class KryoSerdeFactoryProvider implements SerdeFactoryProvider {

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public String name() {
        return "kryo";
    }

    @Override
    public KryoSerdeFactory create(SerdeRegistry registry) {
        return new KryoSerdeFactory(registry);
    }

    @Override
    public KryoSerdeFactory create(Config config, SerdeRegistry registry) {
        long bufferSize = config.getMemorySize("bufferSize").toBytes();
        long maxBufferSize = config.getMemorySize("maxBufferSize").toBytes();
        if (bufferSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("bufferSize " + bufferSize + " is out of range");
        }
        if (maxBufferSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBufferSize " + maxBufferSize + " is out of range");
        }
        KryoSerdeFactory factory = new KryoSerdeFactory((int) bufferSize, (int) maxBufferSize, registry);

        Config settings = config.getConfig("settings");
        boolean registrationRequired = settings.getBoolean("registrationRequired");
        boolean warnUnregisteredClasses = settings.getBoolean("warnUnregisteredClasses");
        boolean references = settings.getBoolean("references");
        boolean copyReferences = settings.getBoolean("copyReferences");
        int maxDepth = settings.getInt("maxDepth");
        boolean optimizedGenerics = settings.getBoolean("optimizedGenerics");
        factory.addInitializer(kryo -> {
            kryo.setRegistrationRequired(registrationRequired);
            kryo.setWarnUnregisteredClasses(warnUnregisteredClasses);
            kryo.setReferences(references);
            kryo.setCopyReferences(copyReferences);
            kryo.setMaxDepth(maxDepth == -1 ? Integer.MAX_VALUE : maxDepth);
            kryo.setOptimizedGenerics(optimizedGenerics);
        });
        try {
            for (var entry : config.getObject("registers").entrySet()) {
                int id = Integer.parseInt(entry.getKey());
                ConfigValue value = entry.getValue();
                if (Objects.requireNonNull(value.valueType()) == ConfigValueType.OBJECT) {
                    Config sub = ((ConfigObject) value).toConfig();
                    Class<?> cls = Class.forName(sub.getString("type"));
                    Serializer<?> serializer;
                    if (sub.hasPath("serializer")) {
                        Class<?> serializerClass = Class.forName(sub.getString("serializer"));
                        serializer = (Serializer<?>) serializerClass.getConstructor().newInstance();
                    } else {
                        serializer = null;
                    }
                    if (serializer == null) {
                        factory.addInitializer(kryo -> kryo.register(cls, id));
                    } else {
                        factory.addInitializer(kryo -> kryo.register(cls, serializer, id));
                    }
                } else {
                    Class<?> cls = Class.forName(entry.getValue().unwrapped().toString());
                    factory.addInitializer(kryo -> kryo.register(cls, id));
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
        return factory;
    }
}
