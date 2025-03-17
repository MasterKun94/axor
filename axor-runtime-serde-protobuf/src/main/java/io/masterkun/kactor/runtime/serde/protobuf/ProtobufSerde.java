package io.masterkun.kactor.runtime.serde.protobuf;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.Serde;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;

public class ProtobufSerde<T extends MessageLite> implements Serde<T> {
    static final int DEFAULT_MAX_MESSAGE_SIZE = 4 * 1024 * 1024;
    private static final ThreadLocal<Reference<byte[]>> bufs = new ThreadLocal<>();

    private final MsgType<T> msgType;
    private final T defaultInstance;
    private final Parser<T> parser;

    @SuppressWarnings("unchecked")
    public ProtobufSerde(MsgType<T> msgType) {
        this.msgType = msgType;
        try {
            defaultInstance = (T) msgType.type().getMethod("getDefaultInstance").invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        this.parser = (Parser<T>) defaultInstance.getParserForType();
    }

    public T getDefaultInstance() {
        return defaultInstance;
    }

    @Override
    public InputStream serialize(T message) {
        return new ProtoInputStream(message, parser);
    }

    @Override
    public T deserialize(InputStream stream) {
        if (stream instanceof ProtoInputStream) {
            ProtoInputStream protoStream = (ProtoInputStream) stream;
            // Optimization for in-memory transport. Returning provided object is safe since protobufs
            // are immutable.
            //
            // However, we can't assume the types match, so we have to verify the parser matches.
            // Today the parser is always the same for a given proto, but that isn't guaranteed. Even
            // if not, using the same MethodDescriptor would ensure the parser matches and permit us
            // to enable this optimization.
            if (protoStream.parser() == parser) {
                try {
                    @SuppressWarnings("unchecked")
                    T message = (T) ((ProtoInputStream) stream).message();
                    return message;
                } catch (IllegalStateException ignored) {
                    // Stream must have been read from, which is a strange state. Since the point of this
                    // optimization is to be transparent, instead of throwing an error we'll continue,
                    // even though it seems likely there's a bug.
                }
            }
        }
        CodedInputStream cis = null;
        try {
            if (stream instanceof KnownLength) {
                int size = stream.available();
                if (size > 0 && size <= DEFAULT_MAX_MESSAGE_SIZE) {
                    Reference<byte[]> ref;
                    // buf should not be used after this method has returned.
                    byte[] buf;
                    if ((ref = bufs.get()) == null || (buf = ref.get()) == null || buf.length < size) {
                        buf = new byte[size];
                        bufs.set(new WeakReference<>(buf));
                    }

                    int remaining = size;
                    while (remaining > 0) {
                        int position = size - remaining;
                        int count = stream.read(buf, position, remaining);
                        if (count == -1) {
                            break;
                        }
                        remaining -= count;
                    }

                    if (remaining != 0) {
                        int position = size - remaining;
                        throw new RuntimeException("size inaccurate: " + size + " != " + position);
                    }
                    cis = CodedInputStream.newInstance(buf, 0, size);
                } else if (size == 0) {
                    return defaultInstance;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (cis == null) {
            cis = CodedInputStream.newInstance(stream);
        }
        // Pre-create the CodedInputStream so that we can remove the size limit restriction
        // when parsing.
        cis.setSizeLimit(Integer.MAX_VALUE);

        try {
            return parseFrom(cis);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private T parseFrom(CodedInputStream stream) throws InvalidProtocolBufferException {
        T message = parser.parseFrom(stream);
        try {
            stream.checkLastTagWas(0);
            return message;
        } catch (InvalidProtocolBufferException e) {
            e.setUnfinishedMessage(message);
            throw e;
        }
    }

    @Override
    public MsgType<T> getType() {
        return msgType;
    }

    @Override
    public String getImpl() {
        return "protobuf";
    }
}
