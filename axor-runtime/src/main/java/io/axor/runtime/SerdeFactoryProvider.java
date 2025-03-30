package io.axor.runtime;

import com.typesafe.config.Config;

public interface SerdeFactoryProvider extends Provider<SerdeFactory> {
    @Override
    default String group() {
        return "serde";
    }

    @Override
    default SerdeFactory create() {
        throw new UnsupportedOperationException();
    }

    @Override
    default SerdeFactory create(Config config) {
        throw new UnsupportedOperationException();
    }

    @Override
    default SerdeFactory createFromRootConfig(Config rootConfig) {
        throw new UnsupportedOperationException();
    }

    SerdeFactory create(SerdeRegistry registry);

    default SerdeFactory createFromRootConfig(Config rootConfig, SerdeRegistry registry) {
        return create(rootConfig.getConfig(configPrefix()), registry);
    }

    SerdeFactory create(Config config, SerdeRegistry registry);
}
