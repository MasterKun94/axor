package io.axor.cluster.membership;

import java.util.function.Function;

public sealed interface MetaKey<T> permits MetaKeys.AbstractMetaKey {
    static MetaKeyBuilder builder(int id) {
        return new MetaKeyBuilder().id(id);
    }

    int id();

    String name();

    String description();

    T get(MetaInfo metaInfo);

    boolean metaEquals(MetaInfo left, MetaInfo right);

    boolean contains(MetaInfo metaInfo, T value);

    boolean containsKey(MetaInfo metaInfo);

    Action upsert(T value);

    Action update(Function<T, T> updateFunc);

    Action delete();

    sealed interface Action permits MetaKeys.Upsert, MetaKeys.Update, MetaKeys.Delete {
    }
}
