package io.masterkun.axor.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A specialized {@code InputStream} that reads from a byte array and implements the
 * {@link Serde.Drainable} and {@link Serde.KnownLength} interfaces. This class is designed to be
 * used in scenarios where data needs to be efficiently read from a byte array, and it can also
 * provide information about the number of bytes available and drain its content to an output
 * stream.
 *
 * <p>This class extends {@code InputStream} and provides methods to read data from a byte array,
 * skip bytes, and determine the number of bytes available. It also implements the {@code Drainable}
 * interface, allowing it to transfer all available data to an output stream, and the
 * {@code KnownLength} interface, providing the number of bytes that can be read without blocking.
 *
 * @see Serde.Drainable
 * @see Serde.KnownLength
 */
public sealed class SerdeByteArrayInputStream extends InputStream
        implements Serde.Drainable, Serde.KnownLength
        permits SerdeByteArrayInputStreamAdaptor {
    protected byte[] buf;
    protected int limit = -1;
    protected int pos = -1;

    protected SerdeByteArrayInputStream() {
    }

    public SerdeByteArrayInputStream(byte[] buf) {
        this.buf = buf;
        this.limit = buf.length;
    }

    public SerdeByteArrayInputStream(byte[] buf, int pos, int limit) {
        if (pos < 0) {
            throw new IllegalArgumentException("pos < 0");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit < 0");
        }
        this.buf = buf;
        this.pos = pos;
        this.limit = limit;
    }

    protected void init() {
    }

    @Override
    public int drainTo(OutputStream stream) throws IOException {
        init();
        int available = limit - pos;
        stream.write(buf, pos, available);
        pos = limit;
        return available;
    }

    @Override
    public int read() {
        init();
        return buf[pos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        init();
        int read = Math.min(len, limit - pos);
        if (read == 0) {
            return -1;
        }
        System.arraycopy(buf, pos, b, off, read);
        pos += read;
        return read;
    }

    @Override
    public long skip(long n) {
        init();
        int skip = Math.min((int) n, limit - pos);
        pos += skip;
        return skip;
    }

    @Override
    public int available() {
        init();
        return limit - pos;
    }

    @Override
    public void close() {
    }
}
