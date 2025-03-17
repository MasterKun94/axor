package io.masterkun.kactor.runtime;

import io.masterkun.kactor.runtime.impl.DefaultEventDispatcherGroup;
import io.masterkun.kactor.runtime.impl.DefaultEventDispatcherGroupBuilder;
import io.masterkun.kactor.testkit.EventExecutorTestKit;
import org.junit.Test;

public class EventDispatcherTest {

    @Test
    public void testDefault() throws Exception {
        DefaultEventDispatcherGroup group = new DefaultEventDispatcherGroupBuilder()
                .build();
        new EventExecutorTestKit(group).test();
        group.shutdownAsync().join();
    }

}
