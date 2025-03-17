package io.masterkun.axor.runtime.impl;

import com.typesafe.config.ConfigFactory;
import io.masterkun.axor.runtime.EventDispatcherGroupBuilderProvider;
import io.masterkun.axor.runtime.Registry;
import org.junit.Assert;
import org.junit.Test;

public class DefaultEventDispatcherGroupBuilderProviderTest {

    @Test
    public void test() {
        var provider = Registry.getByName(EventDispatcherGroupBuilderProvider.class, "default");
        var builder = provider.createFromRootConfig(ConfigFactory.load());
        Assert.assertTrue(builder instanceof DefaultEventDispatcherGroupBuilder);
    }
}
