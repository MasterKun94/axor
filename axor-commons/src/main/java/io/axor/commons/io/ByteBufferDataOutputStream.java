package io.axor.commons.io;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class ByteBufferDataOutputStream extends OutputStream implements DataOutput {
    private final boolean direct;
    private final int maxCapacity;
    private ByteBuffer buffer;
    private DataOutputStream utf8out;

    public ByteBufferDataOutputStream(boolean direct, int initialCapacity, int maxCapacity) {
        this.direct = direct;
        this.maxCapacity = maxCapacity <= 0 ? Integer.MAX_VALUE : maxCapacity;
        this.buffer = direct ?
                ByteBuffer.allocateDirect(initialCapacity) :
                ByteBuffer.allocate(initialCapacity);
    }

    private void ensureCapacity(int required) {
        if (buffer.remaining() < required) {
            ByteBuffer old = buffer;
            int growCapacity = old.capacity() << 1;
            if (maxCapacity <= growCapacity) {
                throw new java.nio.BufferOverflowException();
            }
            buffer = direct ?
                    ByteBuffer.allocateDirect(growCapacity) :
                    ByteBuffer.allocate(growCapacity);
            buffer.put(old.flip());
        }
    }

    public void write(int b) {
        ensureCapacity(1);
        buffer.put((byte) b);
    }

    public void write(byte[] bytes, int offset, int length) {
        ensureCapacity(length);
        buffer.put(bytes, offset, length);
    }

    @Override
    public void writeBoolean(boolean v) {
        ensureCapacity(1);
        buffer.put((byte) (v ? 1 : 0));
    }

    @Override
    public void writeByte(int v) {
        ensureCapacity(1);
        buffer.put((byte) v);
    }

    @Override
    public void writeShort(int v) {
        ensureCapacity(2);
        buffer.putShort((short) v);
    }

    @Override
    public void writeChar(int v) {
        ensureCapacity(2);
        buffer.putChar((char) v);
    }

    @Override
    public void writeInt(int v) {
        ensureCapacity(4);
        buffer.putInt(v);
    }

    @Override
    public void writeLong(long v) {
        ensureCapacity(8);
        buffer.putLong(v);
    }

    @Override
    public void writeFloat(float v) {
        ensureCapacity(4);
        buffer.putFloat(v);
    }

    @Override
    public void writeDouble(double v) {
        ensureCapacity(8);
        buffer.putDouble(v);
    }

    @Override
    public void writeBytes(String s) {
        int len = s.length();
        ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            buffer.put((byte) s.charAt(i));
        }
    }

    @Override
    public void writeChars(String s) {
        int len = s.length();
        ensureCapacity(len << 1);
        for (int i = 0; i < len; i++) {
            buffer.putChar(s.charAt(i));
        }
    }

    @Override
    public void writeUTF(String s) {
        DataOutputStream out = utf8out;
        if (out == null) {
            // Suppress a warning since the stream is closed in the close() method
            utf8out = out = new DataOutputStream(this);
        }
        try {
            out.writeUTF(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void reset() {
        buffer.clear();
    }
}
