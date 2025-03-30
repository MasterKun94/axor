package io.axor.runtime;

/**
 * The {@code SerdeFactory} interface defines a factory for creating instances of {@link Serde} for
 * specific message types. Implementations of this interface provide the ability to check if a given
 * message type is supported and to create the corresponding {@code Serde} instance. Additionally,
 * it provides access to the implementation name and the associated {@link SerdeRegistry}.
 *
 * <p>This interface is designed to be used in systems that require dynamic creation of serializers
 * and deserializers based on the message type, such as in messaging systems, data processing
 * pipelines, and other scenarios where serialization and deserialization are needed.
 */
public interface SerdeFactory {
    /**
     * Checks if the specified message type is supported by this {@code SerdeFactory}.
     *
     * @param type the {@code MsgType} to check for support
     * @return true if the message type is supported, false otherwise
     */
    boolean support(MsgType<?> type);

    /**
     * Creates a {@code Serde} instance for the specified message type.
     *
     * @param <T>  the type of the object that can be serialized and deserialized
     * @param type the {@code MsgType} representing the type of objects to be serialized and
     *             deserialized
     * @return a new {@code Serde} instance for the given message type
     * @throws IllegalArgumentException if the specified message type is not supported
     */
    <T> Serde<T> create(MsgType<T> type);

    /**
     * Returns the implementation name of the serde factory.
     *
     * @return the name of the implementation
     */
    String getImpl();

    /**
     * Returns the {@link SerdeRegistry} associated with this serde factory.
     *
     * @return the {@code SerdeRegistry} instance
     */
    SerdeRegistry getSerdeRegistry();
}
