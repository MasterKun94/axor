package io.masterkun.kactor.testkit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.masterkun.kactor.api.ActorSystem;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.runtime.impl.DefaultEventDispatcherGroup;
import io.masterkun.kactor.runtime.impl.NoopStreamServer;

public interface LocalActorSystem {
    static ActorSystem getOrCreate(String name) {
        Config config = ConfigFactory.load();
        return ActorSystem.create(name,
                new NoopStreamServer(SerdeRegistry.defaultInstance(), name),
                new DefaultEventDispatcherGroup("test-executor", 2),
                config);
    }
}
