package io.axor.commons.config;

import java.math.BigInteger;

/**
 * An immutable class representing an amount of memory.  Use static factory methods such as
 * {@link MemorySize#ofBytes(BigInteger)} to create instances.
 *
 * @since 1.3.0
 */
public final class MemorySize {

    private final BigInteger bytes;

    private MemorySize(BigInteger bytes) {
        if (bytes.signum() < 0)
            throw new IllegalArgumentException("Attempt to construct ConfigMemorySize with " +
                                               "negative number: " + bytes);
        this.bytes = bytes;
    }

    /**
     * Constructs a ConfigMemorySize representing the given number of bytes.
     *
     * @param bytes a number of bytes
     * @return an instance representing the number of bytes
     * @since 1.3.0
     */
    public static MemorySize ofBytes(BigInteger bytes) {
        return new MemorySize(bytes);
    }

    /**
     * Constructs a ConfigMemorySize representing the given number of bytes.
     *
     * @param bytes a number of bytes
     * @return an instance representing the number of bytes
     */
    public static MemorySize ofBytes(long bytes) {
        return new MemorySize(BigInteger.valueOf(bytes));
    }

    /**
     * Gets the size in bytes.
     *
     * @return how many bytes
     * @throws IllegalArgumentException when memory value in bytes doesn't fit in a long value.
     *                                  Consider using {@link #toBytesBigInteger} in this case.
     * @since 1.3.0
     */
    public long toBytes() {
        if (bytes.bitLength() < 64)
            return bytes.longValue();
        else
            throw new IllegalArgumentException(
                    "size-in-bytes value is out of range for a 64-bit long: '" + bytes + "'");
    }

    public int toInt() {
        if (bytes.bitLength() < 32)
            return bytes.intValue();
        else
            throw new IllegalArgumentException(
                    "size-in-bytes value is out of range for a 32-bit int: '" + bytes + "'");

    }

    /**
     * Gets the size in bytes. The behavior of this method is the same as that of the
     * {@link #toBytes()} method, except that the number of bytes returned as a BigInteger value.
     * Use it when memory value in bytes doesn't fit in a long value.
     *
     * @return how many bytes
     */
    public BigInteger toBytesBigInteger() {
        return bytes;
    }

    @Override
    public String toString() {
        return "ConfigMemorySize(" + bytes + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof MemorySize) {
            return ((MemorySize) other).bytes.equals(this.bytes);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // in Java 8 this can become Long.hashCode(bytes)
        return bytes.hashCode();
    }

}

