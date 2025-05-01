package io.axor.cp.kvstore;

import com.google.protobuf.ByteString;
import io.axor.commons.util.ByteArray;

import java.nio.ByteBuffer;

sealed interface StoreKey {
    static ByteBuffer keyToBytes(StoreKey key, ByteBuffer reuse) {
        int start = reuse.position();
        key.writeTo(reuse);
        return reuse.slice(start, reuse.position() - start);
    }

    static StoreKey bytesToKey(ByteBuffer buffer) {
        long id = buffer.getLong();
        if (buffer.hasRemaining()) {
            byte b = buffer.get();
            return switch (b) {
                case ChildKey.FLAG -> new ChildKey(id, ByteString.copyFrom(buffer));
                case DataKey.FLAG -> new DataKey(id);
                default -> throw new IllegalArgumentException("unknown flag: " + b + ", id: " + id);
            };
        } else {
            return new HeadKey(id);
        }
    }

    static ByteBuffer keyPrefix(long id, ByteBuffer reuse) {
        int start = reuse.position();
        reuse.putLong(id);
        return reuse.slice(start, 8);
    }

    static ByteBuffer childKeyPrefix(long id, ByteBuffer reuse) {
        int start = reuse.position();
        reuse.putLong(id).put(ChildKey.FLAG);
        return reuse.slice(start, 9);
    }

    void writeTo(ByteBuffer buffer);

    byte[] toByteArray();

    long id();

    record HeadKey(long id) implements StoreKey {
        @Override
        public void writeTo(ByteBuffer buffer) {
            buffer.putLong(id);
        }

        @Override
        public byte[] toByteArray() {
            byte[] b = new byte[8];
            ByteArray.setLong(b, 0, id);
            return b;
        }
    }

    record ChildKey(long id, ByteString childName) implements StoreKey {
        static final byte FLAG = 1;

        ChildKey(long id, String childName) {
            this(id, ByteString.copyFromUtf8(childName));
        }

        @Override
        public void writeTo(ByteBuffer buffer) {
            buffer.putLong(id).put(FLAG).put(childName.asReadOnlyByteBuffer());
        }

        @Override
        public byte[] toByteArray() {
            byte[] b = new byte[9 + childName.size()];
            ByteArray.setLong(b, 0, id);
            b[8] = FLAG;
            childName.copyTo(b, 9);
            return b;
        }
    }

    record DataKey(long id) implements StoreKey {
        static final byte FLAG = 127;

        @Override
        public void writeTo(ByteBuffer buffer) {
            buffer.putLong(id).put(FLAG);
        }

        @Override
        public byte[] toByteArray() {
            byte[] b = new byte[9];
            ByteArray.setLong(b, 0, id);
            b[8] = FLAG;
            return b;
        }
    }
}
