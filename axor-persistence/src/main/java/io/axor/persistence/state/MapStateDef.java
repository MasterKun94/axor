package io.axor.persistence.state;

import com.typesafe.config.Config;
import org.jetbrains.annotations.Nullable;

public non-sealed abstract class MapStateDef<K, V, C> extends StateDef {

    protected MapStateDef(String name, ShareableScope scope, Config config) {
        super(name, scope, config);
    }

    @Nullable
    public abstract V command(K key, V prev, C command);
}
