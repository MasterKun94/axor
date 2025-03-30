package io.axor.runtime;

import io.axor.runtime.impl.DefaultEventDispatcherGroup;
import io.axor.runtime.impl.DefaultEventDispatcherGroupBuilder;
import io.axor.testkit.EventExecutorTestKit;
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
