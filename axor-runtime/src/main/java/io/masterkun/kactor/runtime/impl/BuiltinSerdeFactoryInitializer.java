package io.masterkun.kactor.runtime.impl;

import io.masterkun.kactor.runtime.SerdeRegistry;

public interface BuiltinSerdeFactoryInitializer {
    void initialize(BuiltinSerdeFactory factory, SerdeRegistry registry);
}
