package io.axor.runtime.impl;

import io.axor.runtime.SerdeRegistry;

public interface BuiltinSerdeFactoryInitializer {
    void initialize(BuiltinSerdeFactory factory, SerdeRegistry registry);
}
