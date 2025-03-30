package io.axor.runtime.stream.grpc;

import com.google.protobuf.Timestamp;
import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamDefinition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamDefinitionBinaryMarshallerTest {

    @Test
    public void testParseAsciiStringValidInput() {
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        StreamDefinitionBinaryMarshaller marshaller =
                new StreamDefinitionBinaryMarshaller(registry);
        StreamDefinition<?> def = new StreamDefinition<>(
                new StreamAddress("host", 123, "service", "call"),
                registry.create(MsgType.of(Timestamp.class))
        );
        assertEquals(def, marshaller.parseAsciiString(marshaller.toAsciiString(def)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseAsciiStringInvalidInput() {
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        StreamDefinitionBinaryMarshaller marshaller =
                new StreamDefinitionBinaryMarshaller(registry);

        String serialized = "invalidInput";
        marshaller.parseAsciiString(serialized);
    }
}
