package io.axor.runtime.serde.protobuf;

import com.typesafe.config.Config;
import io.axor.runtime.SerdeFactory;
import io.axor.runtime.SerdeFactoryProvider;
import io.axor.runtime.SerdeRegistry;

public class ProtobufSerdeFactoryProvider implements SerdeFactoryProvider {

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public String name() {
        return "protobuf";
    }

    @Override
    public SerdeFactory create(SerdeRegistry registry) {
        return new ProtobufSerdeFactory(registry);
    }

    @Override
    public SerdeFactory createFromRootConfig(Config rootConfig, SerdeRegistry registry) {
        return new ProtobufSerdeFactory(registry);
    }

    @Override
    public SerdeFactory create(Config config, SerdeRegistry registry) {
        return new ProtobufSerdeFactory(registry);
    }
}
