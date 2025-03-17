package io.masterkun.axor.runtime.impl;

import io.masterkun.axor.runtime.SerdeRegistry;

public interface BuiltinSerdeFactoryInitializer {
    void initialize(BuiltinSerdeFactory factory, SerdeRegistry registry);
}
