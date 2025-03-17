package io.masterkun.axor.runtime;

import com.typesafe.config.Config;

public interface Provider<T> {
    default String configPrefix() {
        return "axor.runtime." + group() + "." + name();
    }

    int priority();

    String group();

    String name();

    T create();

    T create(Config config);

    default T createFromRootConfig(Config rootConfig) {
        return create(rootConfig.getConfig(configPrefix()));
    }
}
