package io.axor.cluster.membership;

import com.typesafe.config.Config;

public class DefaultSplitBrainResolverProvider implements SplitBrainResolverProvider {
    @Override
    public int priority() {
        return 10;
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public SplitBrainResolver create() {
        return new DefaultSplitBrainResolver(1, 1);
    }

    @Override
    public SplitBrainResolver create(Config config) {
        return new DefaultSplitBrainResolver(
                config.getInt("minRequireMembers"),
                config.getInt("minInitialMembers"));
    }
}
