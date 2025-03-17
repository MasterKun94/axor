package io.masterkun.axor.testkit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.impl.DefaultEventDispatcherGroup;
import io.masterkun.axor.runtime.impl.NoopStreamServer;

public interface LocalActorSystem {
    static ActorSystem getOrCreate(String name) {
        Config config = ConfigFactory.load();
        return ActorSystem.create(name,
                new NoopStreamServer(SerdeRegistry.defaultInstance(), name),
                new DefaultEventDispatcherGroup("test-executor", 2),
                config);
    }
}
