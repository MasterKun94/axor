package io.masterkun.kactor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;

public interface KryoInitializer {
    void initialize(Kryo kryo);
}
