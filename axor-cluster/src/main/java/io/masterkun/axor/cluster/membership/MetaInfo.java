package io.masterkun.axor.cluster.membership;

import io.masterkun.axor.commons.collection.IntCollections;
import io.masterkun.axor.commons.collection.IntObjectHashMap;
import io.masterkun.axor.commons.collection.IntObjectMap;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public final class MetaInfo {
    public static final MetaInfo EMPTY = new MetaInfo(IntCollections.unmodifiableMap(IntCollections.emptyMap()));

    private final IntObjectMap<BytesHolder> map;


    public MetaInfo(IntObjectMap<BytesHolder> map) {
        this.map = IntCollections.unmodifiableMap(map);
    }

    IntObjectMap<BytesHolder> unwrap() {
        return map;
    }

    public <T> T get(MetaKey<T> opt) {
        return opt.get(this);
    }

    public MetaInfo transform(MetaKey.Action... actions) {
        return transform(Arrays.asList(actions));
    }

    public MetaInfo transform(Iterable<MetaKey.Action> actions) {
        IntObjectMap<BytesHolder> map = new IntObjectHashMap<>();
        map.putAll(this.map);
        for (MetaKey.Action action : actions) {
            switch (action) {
                case MetaKeys.Upsert(var id, var value) -> map.put(id, new BytesHolder(value));
                case MetaKeys.Delete(var id) -> map.remove(id);
                case MetaKeys.Update(var id, var func) -> map.compute(id, (k, v) -> {
                    byte[] res = v == null ?
                            func.apply(null, 0, 0) :
                            func.apply(v.bytes, v.offset, v.length);
                    return new BytesHolder(res);
                });
            }
        }
        return new MetaInfo(map);
    }

    public int size() {
        return map.size();
    }

    @Override
    public String toString() {
        return map.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MetaInfo metaInfo = (MetaInfo) o;
        return Objects.equals(map, metaInfo.map);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(map);
    }

    public record BytesHolder(byte[] bytes, int offset, int length) {
        public BytesHolder(byte[] bytes) {
            this(bytes, 0, bytes.length);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            BytesHolder holder = (BytesHolder) o;
            return Arrays.equals(
                    bytes, offset, length + offset,
                    holder.bytes, holder.offset, holder.length + holder.offset);
        }

        @Override
        public int hashCode() {
            return ByteBuffer.wrap(bytes, offset, length).hashCode();
        }
    }
}
