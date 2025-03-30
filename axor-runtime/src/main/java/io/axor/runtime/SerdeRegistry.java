package io.axor.runtime;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import io.axor.commons.collection.IntObjectHashMap;
import io.axor.commons.collection.IntObjectMap;
import io.axor.runtime.impl.BuiltinSerde;
import io.axor.runtime.impl.BuiltinSerdeFactory;
import io.axor.runtime.impl.NoopSerdeFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A registry for managing and creating instances of {@code Serde} (Serializer/Deserializer) based
 * on message types. This class provides a way to register, find, and create serde factories and
 * their associated serdes.
 */
public class SerdeRegistry {
    public static final String CONFIG_PATH = Constants.RUNTIME_CONFIG_PATH + ".registry.serde";
    private static volatile SerdeRegistry DEFAULT;

    private final Map<MsgType<?>, Integer> typeToId = new HashMap<>();
    private final IntObjectMap<MsgType<?>> idToType = new IntObjectHashMap<>();
    private final List<SerdeFactory> factories;

    private SerdeRegistry() {
        factories = Registry.listAvailable(SerdeFactoryProvider.class).stream()
                .sorted(Comparator.comparingInt(SerdeFactoryProvider::priority).reversed())
                .map(provider -> provider.create(this))
                .toList();
    }

    public SerdeRegistry(Config rootConfig) {
        factories = Registry.listAvailable(SerdeFactoryProvider.class).stream()
                .sorted(Comparator.comparingInt(SerdeFactoryProvider::priority).reversed())
                .map(provider -> provider.createFromRootConfig(rootConfig, this))
                .toList();
        if (!rootConfig.hasPath(CONFIG_PATH)) {
            return;
        }
        for (var entry : rootConfig.getObject(CONFIG_PATH).entrySet()) {
            String name = entry.getKey();
            SerdeFactory factory = getFactory(name);
            Config config = ((ConfigObject) entry.getValue()).toConfig();
            try {
                for (var e : config.entrySet()) {
                    if (factory instanceof BuiltinSerdeFactory) {
                        String type = e.getKey();
                        Class<?> clazz = Class.forName(type);
                        int id = config.getInt(type);
                        if (!BuiltinSerde.class.isAssignableFrom(clazz)) {
                            throw new IllegalArgumentException(type + " is not a builtin serde " +
                                                               "class");
                        }
                        register(id, (BuiltinSerde<?>) clazz.getConstructor().newInstance());
                    } else {
                        String type = e.getKey();
                        int id = config.getInt(type);
                        register(id, MsgType.parse(type));
                    }
                }
            } catch (InstantiationException | NoSuchMethodException | InvocationTargetException |
                     IllegalAccessException | ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }

        }
    }

    public static SerdeRegistry defaultInstance() {
        if (DEFAULT == null) {
            synchronized (SerdeRegistry.class) {
                if (DEFAULT == null) {
                    DEFAULT = new SerdeRegistry();
                }
            }
        }
        return DEFAULT;
    }

    private void register(int id, MsgType<?> type) {
        typeToId.put(type, id);
        idToType.put(id, type);
    }

    private <T> void register(int id, BuiltinSerde<T> serde) {
        typeToId.put(serde.getType(), id);
        idToType.put(id, serde.getType());
        getFactory(BuiltinSerdeFactory.class).register(serde);
    }

    public int findIdByType(MsgType<?> type) {
        return typeToId.getOrDefault(type, -1);
    }

    public MsgType<?> getTypeById(int id) {
        MsgType<?> obj = idToType.get(id);
        if (obj == null) {
            throw new IllegalArgumentException("Unknown id " + id);
        }
        return obj;
    }

    public Optional<SerdeFactory> findFactory(String name) {
        if (name.equals("noop")) {
            return Optional.of(new NoopSerdeFactory(this));
        }
        return factories.stream()
                .filter(f -> f.getImpl().equals(name))
                .findFirst();
    }

    public SerdeFactory getFactory(String name) {
        return findFactory(name)
                .orElseThrow(() -> new IllegalArgumentException("No such serde factory: " + name));
    }

    @SuppressWarnings("unchecked")
    public <T extends SerdeFactory> Optional<T> findFactory(Class<T> type) {
        return factories.stream()
                .filter(f -> type.equals(f.getClass()))
                .findFirst()
                .map(o -> (T) o);
    }

    public <T extends SerdeFactory> T getFactory(Class<T> type) {
        return findFactory(type)
                .orElseThrow(() -> new IllegalArgumentException("No such serde factory: " + type));
    }

    public <T> Serde<T> create(MsgType<T> type) {
        return factories.stream()
                .filter(f -> f.support(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Serde type " + type + " is not " +
                                                                "supported"))
                .create(type);
    }

    public <T> Serde<T> create(String name, MsgType<T> type) {
        SerdeFactory factory = getFactory(name);
        if (!factory.support(type)) {
            throw new IllegalArgumentException("Serde type " + type + " is not supported for " + name);
        }
        return factory.create(type);
    }

}
