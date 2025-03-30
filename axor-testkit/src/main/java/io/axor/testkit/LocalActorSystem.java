package io.axor.testkit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.ActorSystem;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.impl.DefaultEventDispatcherGroup;
import io.axor.runtime.impl.NoopStreamServer;

public interface LocalActorSystem {
    static ActorSystem getOrCreate(String name) {
        Config config = ConfigFactory.load();
        return ActorSystem.create(name,
                new NoopStreamServer(SerdeRegistry.defaultInstance(), name),
                new DefaultEventDispatcherGroup("test-executor", 2, 1),
                config);
    }
}
