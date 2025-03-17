package io.masterkun.kactor.runtime.serde.protobuf;

import com.google.protobuf.Timestamp;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.testkit.SerdeTestKit;
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
