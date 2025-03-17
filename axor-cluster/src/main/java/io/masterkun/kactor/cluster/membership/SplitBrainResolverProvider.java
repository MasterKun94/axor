package io.masterkun.kactor.cluster.membership;

import com.typesafe.config.Config;
import io.masterkun.kactor.runtime.Provider;

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
