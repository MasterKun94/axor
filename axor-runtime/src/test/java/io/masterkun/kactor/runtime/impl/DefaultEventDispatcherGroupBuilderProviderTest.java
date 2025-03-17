package io.masterkun.kactor.runtime.impl;

import com.typesafe.config.ConfigFactory;
import io.masterkun.kactor.runtime.EventDispatcherGroupBuilderProvider;
import io.masterkun.kactor.runtime.Registry;
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
