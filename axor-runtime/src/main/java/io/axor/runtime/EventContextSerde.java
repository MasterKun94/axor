package io.axor.runtime;

import io.axor.commons.collection.IntObjectHashMap;
import io.axor.commons.collection.IntObjectMap;
import io.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class EventContextSerde implements BuiltinSerde<EventContext> {
    @Override
    public void doSerialize(EventContext obj, DataOutput out) throws IOException {
        if (obj == EventContext.INITIAL) {
            out.writeShort(0);
            return;
        }
        var map = ((EventContextImpl) obj).map();
        out.writeShort(map.size());
        for (var entry : map.entries()) {
            out.writeInt(entry.key());
            EventContextImpl.BytesHolder value = entry.value();
            out.writeShort(value.len());
            out.write(value.b(), value.off(), value.len());
        }
    }

    @Override
    public EventContext doDeserialize(DataInput in) throws IOException {
        int size = Short.toUnsignedInt(in.readShort());
        if (size == 0) {
            return EventContext.INITIAL;
        }
        IntObjectMap<EventContextImpl.BytesHolder> map = new IntObjectHashMap<>(size);
        for (int i = 0; i < size; i++) {
            int id = in.readInt();
            int len = Short.toUnsignedInt(in.readShort());
            byte[] b = new byte[len];
            in.readFully(b, 0, len);
            map.put(id, new EventContextImpl.BytesHolder(b));
        }
        return new EventContextImpl(map);
    }

    @Override
    public MsgType<EventContext> getType() {
        return MsgType.of(EventContext.class);
    }
}
