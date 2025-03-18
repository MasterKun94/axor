package io.masterkun.axor.commons.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigMemorySize;

import java.net.URI;
import java.time.Duration;
import java.time.Period;
import java.util.Collections;
import java.util.List;

public class DefaultConfigParser implements ConfigParser {
    @Override
    public Object parseFrom(Config config, String key, TypeRef type) {
        return getValue(config, key, type);
    }

    private Object getValue(Config config, String key, TypeRef type) {
        Class<?> clazz = type.parentType();
        try {
            if (!config.hasPath(key)) {
                if (type.nullable() && !type.parentType().isPrimitive()) {
                    return null;
                } else {
                    throw new ConfigParseException(type, "[%s] not found");
                }
            }
            if (type.nullable() && !config.hasPath(key)) {
                if (type.parentType().isPrimitive()) {
                    throw new ConfigParseException(type, "[%s] request primitive type but config " +
                            "value " +
                            "is null");
                } else {
                    return null;
                }
            }
            if (clazz.isRecord()) {
                //noinspection unchecked
                return ConfigMapper.map(config.getConfig(key), (Class<? extends Record>) clazz);
            } else if (clazz.equals(String.class)) {
                return config.getString(key);
            } else if (clazz.equals(Integer.class) || clazz.equals(int.class)) {
                return config.getInt(key);
            } else if (clazz.equals(Long.class) || clazz.equals(long.class)) {
                return config.getLong(key);
            } else if (clazz.equals(Double.class) || clazz.equals(double.class)) {
                return config.getDouble(key);
            } else if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
                return config.getBoolean(key);
            } else if (clazz.equals(Float.class) || clazz.equals(float.class)) {
                return (float) config.getDouble(key);
            } else if (clazz.equals(Duration.class)) {
                return config.getDuration(key);
            } else if (clazz.equals(ConfigMemorySize.class)) {
                return config.getMemorySize(key);
            } else if (clazz.equals(Period.class)) {
                return config.getPeriod(key);
            } else if (clazz.equals(URI.class)) {
                return URI.create(config.getString(key));
            } else if (Enum.class.isAssignableFrom(clazz)) {
                //noinspection unchecked,rawtypes
                return config.getEnum((Class<? extends Enum>) clazz, key);
            } else if (clazz.equals(Config.class)) {
                return config.getConfig(key);
            } else if (List.class.isAssignableFrom(clazz)) {
                if (type.paramTypes().length != 1) {
                    throw new ConfigParseException(type, "[%s] is a list type, but " +
                            type.paramTypes().length + " type parameters");
                }
                Class<?> elemType = type.paramTypes()[0];
                if (elemType.equals(Void.class)) {
                    throw new ConfigParseException(type, "[%s] elemType should not be null");
                }
                if (elemType.equals(String.class)) {
                    return Collections.unmodifiableList(config.getStringList(key));
                } else if (elemType.equals(Integer.class)) {
                    return Collections.unmodifiableList(config.getIntList(key));
                } else if (elemType.equals(Long.class)) {
                    return Collections.unmodifiableList(config.getLongList(key));
                } else if (elemType.equals(Double.class)) {
                    return Collections.unmodifiableList(config.getDoubleList(key));
                } else if (elemType.equals(Boolean.class)) {
                    return Collections.unmodifiableList(config.getBooleanList(key));
                } else if (elemType.equals(Float.class)) {
                    return config.getDoubleList(key).stream().map(Double::floatValue).toList();
                } else if (elemType.equals(Duration.class)) {
                    return Collections.unmodifiableList(config.getDurationList(key));
                } else if (elemType.equals(ConfigMemorySize.class)) {
                    return config.getMemorySizeList(key).stream().toList();
                } else if (elemType.equals(URI.class)) {
                    return config.getStringList(key).stream().map(URI::create).toList();
                } else if (Enum.class.isAssignableFrom(elemType)) {
                    //noinspection unchecked,rawtypes
                    return Collections.unmodifiableList(config.getEnumList((Class<? extends Enum>) elemType, key));
                } else if (elemType.equals(Config.class)) {
                    return Collections.unmodifiableList(config.getConfigList(key));
                } else if (Record.class.isAssignableFrom(elemType)) {
                    //noinspection unchecked
                    return config.getConfigList(key).stream()
                            .map(c -> ConfigMapper.map(c, (Class<? extends Record>) elemType))
                            .toList();
                }
            }
            throw new ConfigParseException(type, "illegal type [" + clazz + "] for config [%s]");
        } catch (ConfigParseException e) {
            e.addKey(key);
            throw e;
        } catch (Exception e) {
            var ex = new ConfigParseException(type, "[%s] config parse error: " + e, e);
            ex.addKey(key);
            throw ex;
        }
    }
}
