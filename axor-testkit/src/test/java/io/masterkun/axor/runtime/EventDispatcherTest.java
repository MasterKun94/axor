package io.masterkun.axor.runtime;

import io.masterkun.axor.runtime.impl.DefaultEventDispatcherGroup;
import io.masterkun.axor.runtime.impl.DefaultEventDispatcherGroupBuilder;
import io.masterkun.axor.testkit.EventExecutorTestKit;
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
