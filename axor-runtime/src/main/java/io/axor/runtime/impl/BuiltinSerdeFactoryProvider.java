package io.axor.runtime.impl;

import com.typesafe.config.Config;
import io.axor.runtime.SerdeFactory;
import io.axor.runtime.SerdeFactoryProvider;
import io.axor.runtime.SerdeRegistry;

import java.lang.reflect.InvocationTargetException;

public class BuiltinSerdeFactoryProvider implements SerdeFactoryProvider {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String name() {
        return "builtin";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public SerdeFactory create(Config config, SerdeRegistry registry) {
        BuiltinSerdeFactory factory = create(registry);
        try {
            for (var registers : config.getConfig("registers").entrySet()) {
                String key = registers.getKey();
                String value = registers.getValue().unwrapped().toString();
                Class msgType = Class.forName(key);
                Class serdeType = Class.forName(value);
                if (!BuiltinSerde.class.isAssignableFrom(msgType)) {
                    throw new IllegalArgumentException("Serde " + serdeType + " does not " +
                                                       "implement BuiltinSerde");
                }
                BuiltinSerde serde = (BuiltinSerde) serdeType.getConstructor().newInstance();
                factory.register(msgType, serde);
            }
        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException |
                 IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return factory;
    }

    @Override
    public BuiltinSerdeFactory create(SerdeRegistry registry) {
        return new BuiltinSerdeFactory(registry);
    }
}
