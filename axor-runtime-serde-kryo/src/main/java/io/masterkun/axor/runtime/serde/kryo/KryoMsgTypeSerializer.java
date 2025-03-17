package io.masterkun.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Unsafe;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unchecked", "rawtypes"})
public class KryoMsgTypeSerializer extends Serializer<MsgType> {
    private final Serializer<Class> classSerializer;

    public KryoMsgTypeSerializer(Serializer<Class> classSerializer) {
        this.classSerializer = classSerializer;
    }

    @Override
    public void write(Kryo kryo, Output output, MsgType object) {
        classSerializer.write(kryo, output, object.type());
        List typeArgs = object.typeArgs();
        output.writeShort(typeArgs.size());
        if (typeArgs.isEmpty()) {
            return;
        }
        for (MsgType o : (List<MsgType>) typeArgs) {
            write(kryo, output, o);
        }
    }

    @Override
    public MsgType read(Kryo kryo, Input input, Class<? extends MsgType> type) {
        Class rawType = classSerializer.read(kryo, input, Class.class);
        int count = input.readShort();
        if (count == 0) {
            return MsgType.of(rawType);
        }
        List<MsgType<?>> typeArgs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            typeArgs.add(read(kryo, input, rawType));
        }
        return Unsafe.msgType(rawType, typeArgs);
    }
}
