package io.axor.runtime.stream.grpc;

import com.google.common.io.ByteStreams;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventContextSerde;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ContextMsgSerde<T> implements BuiltinSerde<ContextMsg<T>> {
    private static final EventContextSerde ctxSerde = new EventContextSerde();
    private final Serde<T> msgSerde;

    public ContextMsgSerde(Serde<T> msgSerde) {
        this.msgSerde = msgSerde;
    }

    @Override
    public void doSerialize(ContextMsg<T> obj, DataOutput out) throws IOException {
        ctxSerde.doSerialize(obj.context(), out);
        if (msgSerde instanceof BuiltinSerde<T> builtin) {
            builtin.doSerialize(obj.msg(), out);
        } else {
            try (InputStream in = msgSerde.serialize(obj.msg())) {
                if (in instanceof Drainable drainable) {
                    drainable.drainTo((OutputStream) out);
                } else {
                    ByteStreams.copy(in, (OutputStream) out);
                }
            }
        }
    }

    @Override
    public ContextMsg<T> doDeserialize(DataInput in) throws IOException {
        EventContext context = ctxSerde.doDeserialize(in);
        if (msgSerde instanceof BuiltinSerde<T> builtin) {
            return new ContextMsg<>(context, builtin.doDeserialize(in));
        } else {
            return new ContextMsg<>(context, msgSerde.deserialize((InputStream) in));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public MsgType<ContextMsg<T>> getType() {
        return (MsgType) MsgType.of(ContextMsg.class);
    }
}
