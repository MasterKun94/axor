package io.masterkun.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;

public class DefaultKryoInitializer implements KryoInitializer {
    @Override
    public void initialize(Kryo kryo) {
        kryo.setWarnUnregisteredClasses(true);
        kryo.setRegistrationRequired(false);
    }
}
