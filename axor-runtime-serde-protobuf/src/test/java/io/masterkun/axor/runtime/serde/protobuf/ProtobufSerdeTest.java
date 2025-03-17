package io.masterkun.axor.runtime.serde.protobuf;

import com.google.protobuf.Timestamp;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.testkit.SerdeTestKit;
import org.junit.Test;

public class ProtobufSerdeTest {

    @Test
    public void testSerde() throws Exception {
        ProtobufSerdeFactory factory = new ProtobufSerdeFactory(SerdeRegistry.defaultInstance());
        Timestamp ts = Timestamp.newBuilder().setSeconds(123).setNanos(111).build();
        SerdeTestKit.of(factory, MsgType.of(Timestamp.class))
                .impl("protobuf")
                .msgType(MsgType.of(Timestamp.class))
                .instanceOf(ProtobufSerde.class)
                .test(ts);
    }

}
