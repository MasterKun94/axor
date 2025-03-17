package io.masterkun.axor.api;

import io.masterkun.axor.runtime.SerdeFactory;
import io.masterkun.axor.runtime.SerdeRegistry;

import java.util.Optional;

/**
 * Abstract base class for initializing serializers and deserializers (serde) in an ActorSystem.
 * <p>
 * This class provides a mechanism to initialize the serialization and deserialization logic
 * for specific types of messages or actors. It is designed to be extended by concrete
 * implementations that provide the actual initialization logic.
 *
 * @param <F> the type of the serde factory used for initialization
 */
public abstract class ActorSystemSerdeInitializer<F extends SerdeFactory> {
    private boolean initialized;

    public boolean maybeInitialize(ActorSystem actorSystem, SerdeRegistry registry) {
        Optional<F> opt = registry.findFactory(getFactoryClass());
        if (opt.isPresent()) {
            if (!initialized) {
                initialized = true;
                initialize(actorSystem, opt.get(), registry);
            }
            return true;
        } else {
            return false;
        }
    }

    protected abstract void initialize(ActorSystem actorSystem, F serdeFactory, SerdeRegistry registry);

    protected abstract Class<F> getFactoryClass();

    public int priority() {
        return 0;
    }
}
