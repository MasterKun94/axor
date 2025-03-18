package io.masterkun.axor.runtime.serde.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.testkit.SerdeTestKit;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void testDeserializeValidMessage() throws Exception {
        ProtobufSerde<Timestamp> serde = new ProtobufSerde<>(MsgType.of(Timestamp.class));
        Timestamp originalTs = Timestamp.newBuilder().setSeconds(123).setNanos(111).build();

        ByteString byteString = originalTs.toByteString();
        InputStream inputStream = new ByteArrayInputStream(byteString.toByteArray());

        Timestamp deserializedTs = serde.deserialize(inputStream);

        assertEquals(originalTs, deserializedTs);
    }

    @Test(expected = RuntimeException.class)
    public void testDeserializeInvalidStream() throws Exception {
        ProtobufSerde<Timestamp> serde = new ProtobufSerde<>(MsgType.of(Timestamp.class));
        InputStream invalidStream = new ByteArrayInputStream(new byte[]{0, 0, 0, 0});

        serde.deserialize(invalidStream);
    }
}
