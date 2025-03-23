package io.masterkun.axor.cluster.serde;

import io.masterkun.axor.cluster.membership.Unsafe;
import io.masterkun.axor.cluster.membership.VectorClock;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.impl.BuiltinSerde;
import io.masterkun.axor.testkit.SerdeTestKit;
import org.junit.Test;

public class VectorClockSerdeTest {
    @Test
    public void testSerde() throws Exception {
        SerdeTestKit<VectorClock> testKit = SerdeTestKit.of(new VectorClockSerde())
                .test(Unsafe.wrapNoCheck(1, 111, 2, 234, 5, 0))
                .test(Unsafe.wrapNoCheck(1, 111))
                .msgType(MsgType.of(VectorClock.class))
                .impl("builtin")
                .instanceOf(BuiltinSerde.class);
    }

}
