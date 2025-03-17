package io.masterkun.kactor.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class SerdeByteArrayInputStreamAdaptor<T> extends SerdeByteArrayInputStream {
    private final Writer<T> writer;
    private final T obj;

    public SerdeByteArrayInputStreamAdaptor(Writer<T> writer, T obj) {
        this.writer = writer;
        this.obj = obj;
    }

    @Override
    public int drainTo(OutputStream stream) throws IOException {
        if (limit == -1) {
            int size = writer.write(stream, obj);
            return pos = limit = size;
        }
        int available = limit - pos;
        stream.write(buf, pos, available);
        pos = limit;
        return available;
    }

    @Override
    protected void init() {
        if (limit == -1) {
            ByteArrayOut baos = new ByteArrayOut();
            int size;
            try {
                size = writer.write(baos, obj);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            buf = baos.getBuf();
            pos = 0;
            limit = size;
        }
    }

    public interface Writer<T> {
        int write(OutputStream stream, T data) throws IOException;
    }

    private static class ByteArrayOut extends ByteArrayOutputStream {
        public byte[] getBuf() {
            return buf;
        }
    }
}
