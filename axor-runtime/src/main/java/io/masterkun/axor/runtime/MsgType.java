package io.masterkun.axor.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a message type that can be used to describe and manipulate types, including
 * parameterized types. This interface provides methods to create, parse, and check the
 * compatibility of different types.
 *
 * @param <T> the type represented by this MsgType
 */
public sealed interface MsgType<T> {

    static <T> MsgType<T> of(Class<T> type) {
        return new Raw<>(type);
    }

    static <T> MsgType<T> of(TypeReference<T> typeRef) {
        return of(typeRef.getType());
    }

    /**
     * Parses the given string representation of a message type and returns the corresponding
     * {@code MsgType} object.
     *
     * @param msgType the string representation of the message type to be parsed
     * @return the {@code MsgType} object that represents the parsed message type
     */
    static MsgType<?> parse(String msgType) {
        return MsgTypeParser.parse(msgType);
    }

    @SuppressWarnings("unchecked")
    private static <T> MsgType<T> of(Type type) {
        if (type instanceof Class) {
            return of((Class<T>) type);
        }
        if (type instanceof ParameterizedType pType) {
            if (pType.getActualTypeArguments().length == 0) {
                return of((Class<T>) pType.getRawType());
            }
            List<MsgType<?>> typeArguments = Arrays.stream(pType.getActualTypeArguments())
                    .map(MsgType::of)
                    .collect(Collectors.toUnmodifiableList());
            if (typeArguments.isEmpty()) {
                return of((Class<T>) pType.getRawType());
            }
            return new Parameterized<>((Class<T>) pType.getRawType(), typeArguments);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    /**
     * Returns the raw class type associated with this message type.
     *
     * @return the raw class type of the message
     */
    Class<T> type();

    /**
     * Returns the list of type arguments associated with this message type. If this message type is
     * a parameterized type, it returns the actual type arguments. For non-parameterized types, this
     * method will return an empty list.
     *
     * @return the list of type arguments, or an empty list if there are no type arguments
     */
    List<MsgType<?>> typeArgs();

    /**
     * Returns the fully qualified name of the type represented by this {@code MsgType} object.
     *
     * @return the fully qualified name of the type
     */
    String qualifiedName();

    /**
     * Returns the simple name of the type represented by this {@code MsgType} object.
     *
     * @return the simple name of the type
     */
    String name();

    /**
     * Checks if the given message type is supported.
     *
     * @param type the message type to check for support
     * @return true if the message type is supported, false otherwise
     */
    boolean isSupport(MsgType<?> type);

    /**
     * Checks if the given class type is supported.
     *
     * @param type the class type to check for support
     * @return true if the class type is supported, false otherwise
     */
    boolean isSupport(Class<?> type);
}

/**
 * A record that represents a raw, non-parameterized message type. This class implements the
 * {@link MsgType} interface to provide a simple and direct way to work with basic types.
 *
 * @param <T> the type represented by this Raw instance
 */
record Raw<T>(Class<T> type) implements MsgType<T> {
    private static final Logger LOG = LoggerFactory.getLogger(Raw.class);

    @Override
    public List<MsgType<?>> typeArgs() {
        return List.of();
    }

    @Override
    public boolean isSupport(MsgType<?> msgType) {
        return isSupport(msgType.type());
    }

    @Override
    public boolean isSupport(Class<?> clazz) {
        return type.isAssignableFrom(clazz);
    }

    @Override
    public String qualifiedName() {
        return type.getName();
    }

    @Override
    public String name() {
        return type.getSimpleName();
    }

    @Override
    public String toString() {
        return name();
    }
}

/**
 * Represents a parameterized message type, which is a specific implementation of the
 * {@link MsgType} interface. This class encapsulates a raw type and its associated type arguments,
 * allowing for the creation and manipulation of parameterized types. It provides methods to check
 * support for other types, generate names, and more.
 *
 * @param <T> the type represented by this parameterized message type
 */
record Parameterized<T>(Class<T> type, List<MsgType<?>> typeArgs) implements MsgType<T> {
    private static final Logger LOG = LoggerFactory.getLogger(Parameterized.class);

    private void buildName(StringBuilder builder, boolean simple) {
        builder.append(simple ? type.getSimpleName() : type.getName());
        if (!typeArgs.isEmpty()) {
            builder.append("<");
            boolean first = true;
            for (MsgType<?> arg : typeArgs) {
                if (first) {
                    first = false;
                } else {
                    builder.append(", ");
                }
                if (arg instanceof Parameterized<?> parameterized) {
                    parameterized.buildName(builder, simple);
                } else {
                    builder.append(simple ? arg.name() : arg.qualifiedName());
                }
            }
            builder.append(">");
        }
    }

    @Override
    public boolean isSupport(MsgType<?> msgType) {
        boolean support = this.type.isAssignableFrom(msgType.type());
        if (!support || msgType.typeArgs().isEmpty()) {
            // TODO Unsafe check for parameterized type with raw type
            return support;
        }
        int size;
        if ((size = typeArgs.size()) != msgType.typeArgs().size()) {
            throw new IllegalArgumentException("typeArgs of " + msgType.type() + " is different");
        }
        for (int i = 0; i < size; i++) {
            if (!typeArgs.get(i).isSupport(msgType.typeArgs().get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isSupport(Class<?> type) {
        // TODO Unsafe check for parameterized type with raw type
        return this.type.isAssignableFrom(type);
    }

    @Override
    public String qualifiedName() {
        StringBuilder builder = new StringBuilder();
        buildName(builder, false);
        return builder.toString();
    }

    @Override
    public String name() {
        StringBuilder builder = new StringBuilder();
        buildName(builder, true);
        return builder.toString();
    }

    @Override
    public String toString() {
        return name();
    }
}
