package io.masterkun.kactor.runtime.serde.protobuf;

import com.typesafe.config.Config;
import io.masterkun.kactor.runtime.SerdeFactory;
import io.masterkun.kactor.runtime.SerdeFactoryProvider;
import io.masterkun.kactor.runtime.SerdeRegistry;

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
