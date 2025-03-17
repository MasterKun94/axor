package io.masterkun.kactor.commons.config;

import com.typesafe.config.Config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

public class ConfigMapper {
    public static <T extends Record> T map(Config config, Class<T> clazz) {
        RecordComponent[] recordComponents = clazz.getRecordComponents();
        Class<?>[] constructorTypes = Arrays.stream(recordComponents)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        Object[] constructorArgs = new Object[constructorTypes.length];
        Config remainConfig = config;
        for (int i = 0; i < recordComponents.length; i++) {
            RecordComponent recordComponent = recordComponents[i];
            if (recordComponent.isAnnotationPresent(ConfigOrigin.class)) {
                continue;
            }
            Class<?> type = constructorTypes[i];
            Class<?>[] typeArgs = new Class[0];
            boolean nullable = false;
            String key = recordComponent.getName();
            ConfigParser parser = new DefaultConfigParser();
            if (recordComponent.isAnnotationPresent(ConfigField.class)) {
                ConfigField configField = recordComponent.getAnnotation(ConfigField.class);
                nullable = configField.nullable();
                if (configField.value() != null && !configField.value().isEmpty()) {
                    key = configField.value();
                }
                typeArgs = configField.typeArges();
                try {
                    parser = configField.parser().getConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException |
                         InvocationTargetException | NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
            TypeRef typeRef = new TypeRef(nullable, type, typeArgs);
            constructorArgs[i] = parser.parseFrom(remainConfig, key, typeRef);
            remainConfig = remainConfig.withoutPath(key);
        }
        for (int i = 0; i < recordComponents.length; i++) {
            RecordComponent recordComponent = recordComponents[i];
            if (recordComponent.isAnnotationPresent(ConfigOrigin.class)) {
                ConfigOrigin anno = recordComponent.getAnnotation(ConfigOrigin.class);
                if (anno.retainAll()) {
                    constructorArgs[i] = config;
                } else {
                    constructorArgs[i] = remainConfig;
                }
            }
        }
        try {
            return clazz.getConstructor(constructorTypes).newInstance(constructorArgs);
        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
