package io.masterkun.axor.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The {@code Serde} interface defines a serializer and deserializer for objects of a specific type.
 * Implementations of this interface provide the ability to convert an object into a byte stream
 * (serialization) and to reconstruct the object from the byte stream (deserialization).
 *
 * <p>This interface is designed to be used in systems that require efficient serialization and
 * deserialization, such as message passing, data storage, and network communication.
 *
 * @param <T> the type of the object that can be serialized and deserialized
 */
public interface Serde<T> {

    static String toString(Serde<?> serde) {
        return serde.getClass().getSimpleName() + "[" + serde.getType().name() + "]";
    }

    /**
     * Serializes the given object into an {@code InputStream}.
     *
     * <p>The returned {@code InputStream} can be of a type that implements the {@link KnownLength}
     * and {@link Drainable} interfaces for optimization. Implementing these interfaces allows the
     * stream to provide information about the number of bytes available without blocking, and to
     * efficiently drain its content to an output stream, respectively. This is particularly useful
     * in scenarios where precise control over data flow and efficient data transfer are required.
     *
     * @param object the object to be serialized
     * @return an {@code InputStream} containing the serialized data of the object, potentially
     * implementing {@link KnownLength} and/or {@link Drainable} for optimization
     * @throws IOException if an I/O error occurs during serialization
     */

    InputStream serialize(T object) throws IOException;

    /**
     * Deserializes the object of type T from the given input stream.
     *
     * <p>The provided {@code InputStream} can be of a type that implements the {@link KnownLength}
     * and {@link Drainable} interfaces for optimization. Implementing these interfaces allows the
     * stream to provide information about the number of bytes available without blocking, and to
     * efficiently drain its content, which can be beneficial in scenarios where precise control
     * over data flow and efficient data processing are required.
     *
     * @param stream the input stream containing the serialized data, potentially implementing
     *               {@link KnownLength} and/or {@link Drainable} for optimization
     * @return the deserialized object of type T
     * @throws IOException if an I/O error occurs during deserialization
     */
    T deserialize(InputStream stream) throws IOException;

    /**
     * Returns the message type associated with this Serde.
     *
     * @return the {@code MsgType} representing the type of objects that this Serde can serialize
     * and deserialize
     */
    MsgType<T> getType();

    /**
     * @return Serde's implementation
     */
    String getImpl();

    /**
     * The {@code KnownLength} interface is a marker interface that indicates an object can provide
     * the number of bytes that can be read (or skipped over) from this source without blocking by
     * the next caller.
     *
     * <p>This interface is typically used in conjunction with input streams or other data sources
     * where it is beneficial to know the amount of data available before reading. Implementations
     * of this interface should provide a method to return the number of bytes that can be read
     * without blocking.
     */
    interface KnownLength {
        int available() throws IOException;
    }

    /**
     * The {@code Drainable} interface defines a contract for objects that can drain their content
     * to an output stream. Implementations of this interface provide the ability to transfer all
     * available data from the source to the specified output stream. This is useful in scenarios
     * where you need to efficiently move data from one source to another, such as when copying or
     * transferring data between different streams or buffers.
     *
     * <p>The primary method, {@link #drainTo(OutputStream)}, should be implemented to transfer all
     * available data from the source to the given output stream. The method returns the number of
     * bytes transferred.
     */
    interface Drainable {
        int drainTo(OutputStream stream) throws IOException;
    }
}
