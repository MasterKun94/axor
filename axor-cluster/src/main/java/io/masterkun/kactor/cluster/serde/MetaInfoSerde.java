package io.masterkun.kactor.cluster.serde;

import io.masterkun.kactor.cluster.internal.ByteArray;
import io.masterkun.kactor.cluster.membership.MetaInfo;
import io.masterkun.kactor.cluster.membership.Unsafe;
import io.masterkun.kactor.commons.collection.IntObjectHashMap;
import io.masterkun.kactor.commons.collection.IntObjectMap;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MetaInfoSerde implements BuiltinSerde<MetaInfo> {
    @Override
    public void doSerialize(MetaInfo obj, DataOutput out) throws IOException {
        IntObjectMap<MetaInfo.BytesHolder> map = Unsafe.unwrap(obj);
        int size = map.size();
        out.writeShort(size);
        if (size == 0) {
            return;
        }
        int total = size * 6;
        for (MetaInfo.BytesHolder holder : map.values()) {
            total += holder.length();
        }
        out.writeShort(total);
        for (var entry : map.entries()) {
            int key = entry.key();
            MetaInfo.BytesHolder holder = entry.value();
            out.writeInt(key);
            out.writeShort(holder.length());
            out.write(holder.bytes(), holder.offset(), holder.length());
        }
    }

    @Override
    public MetaInfo doDeserialize(DataInput in) throws IOException {
        int size = in.readShort();
        if (size == 0) {
            return MetaInfo.EMPTY;
        }
        IntObjectMap<MetaInfo.BytesHolder> map = new IntObjectHashMap<>(size);
        int total = in.readShort();
        byte[] bytes = new byte[total];
        in.readFully(bytes);
        int offset = 0;
        while (offset < total) {
            int key = ByteArray.getInt(bytes, offset);
            int length = ByteArray.getShort(bytes, offset += 4);
            var holder = new MetaInfo.BytesHolder(bytes, offset += 2, length);
            map.put(key, holder);
            offset += length;
        }
        return new MetaInfo(map);
    }

    @Override
    public MsgType<MetaInfo> getType() {
        return MsgType.of(MetaInfo.class);
    }
}
