package io.axor.cluster.membership;

import com.typesafe.config.Config;
import io.axor.runtime.Provider;

public interface SplitBrainResolverProvider extends Provider<SplitBrainResolver> {
    @Override
    default String group() {
        return "splitBrainResolver";
    }

    @Override
    default SplitBrainResolver createFromRootConfig(Config rootConfig) {
        throw new UnsupportedOperationException();
    }
}
