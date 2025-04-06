package io.axor.runtime.stream.grpc;

import com.google.common.io.ByteStreams;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventContextSerde;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.Signal;
import io.axor.runtime.impl.BuiltinSerde;
import io.axor.runtime.stream.grpc.StreamRecord.ContextMsg;
import io.axor.runtime.stream.grpc.StreamRecord.ContextSignal;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ContextMsgSerde<T> implements BuiltinSerde<StreamRecord<T>> {
    private static final EventContextSerde ctxSerde = new EventContextSerde();
    private final Serde<T> msgSerde;
    private final Serde<Signal> signalSerde;

    public ContextMsgSerde(Serde<T> msgSerde, SerdeRegistry serdeRegistry) {
        this.msgSerde = msgSerde;
        this.signalSerde = serdeRegistry.create(MsgType.of(Signal.class));
    }

    @Override
    public void doSerialize(StreamRecord<T> obj, DataOutput out) throws IOException {
        if (obj instanceof ContextMsg(var ctx, var msg)) {
            ctxSerde.doSerialize(ctx, out);
            out.writeShort(0);
            if (msgSerde instanceof BuiltinSerde<T> builtin) {
                builtin.doSerialize(msg, out);
                return;
            }
            try (InputStream in = msgSerde.serialize(msg)) {
                if (in instanceof Drainable drainable) {
                    drainable.drainTo((OutputStream) out);
                } else {
                    ByteStreams.copy(in, (OutputStream) out);
                }
            }
        } else if (obj instanceof ContextSignal(var ctx, var signal)) {
            ctxSerde.doSerialize(ctx, out);
            out.writeShort(1);
            try (var in = signalSerde.serialize(signal)) {
                if (in instanceof Drainable drainable) {
                    drainable.drainTo((OutputStream) out);
                } else {
                    ByteStreams.copy(in, (OutputStream) out);
                }
            }
        }

    }

    @Override
    public StreamRecord<T> doDeserialize(DataInput in) throws IOException {
        EventContext context = ctxSerde.doDeserialize(in);
        return switch (in.readShort()) {
            case 0 -> msgSerde instanceof BuiltinSerde<T> builtin ?
                    new ContextMsg<>(context, builtin.doDeserialize(in)) :
                    new ContextMsg<>(context, msgSerde.deserialize((InputStream) in));
            case 1 -> new ContextSignal<>(context, signalSerde.deserialize((InputStream) in));
            default -> throw new RuntimeException("Illegal serializing content");
        };

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public MsgType<StreamRecord<T>> getType() {
        return (MsgType) MsgType.of(StreamRecord.class);
    }
}
