package io.masterkun.axor.runtime.stream.grpc;

import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.masterkun.axor.runtime.Serde;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class Marshallers {
    private static final Logger LOG = LoggerFactory.getLogger(Marshallers.class);

    private static final MethodDescriptor.Marshaller<InputStream> FORWARDING_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {

                @Override
                public InputStream stream(InputStream stream) {
                    return stream;
                }

                @Override
                public InputStream parse(InputStream stream) {
                    return stream;
                }
            };

    private static final Class<? extends Serde<?>> PROTO_SERDE_CLASS;
    private static final DefaultMessageGetter GETTER;

    static {
        Class<?> cls = null;
        try {
            cls = Class.forName("io.masterkun.axor.runtime.serde.protobuf.ProtobufSerde");
        } catch (ClassNotFoundException e) {
            // do nothing
        }
        //noinspection unchecked
        PROTO_SERDE_CLASS = (Class<? extends Serde<?>>) cls;
        if (cls == null) {
            LOG.warn("ProtobufSerde class not found");
            GETTER = null;
        } else {
            GETTER = new DefaultMessageGetter();
        }
    }

    private static boolean isProtoSerde(Serde<?> serde) {
        return PROTO_SERDE_CLASS != null && PROTO_SERDE_CLASS.isAssignableFrom(serde.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <T> MethodDescriptor.Marshaller<T> create(Serde<T> serde) {
        return isProtoSerde(serde) ?
                (MethodDescriptor.Marshaller<T>) ProtoUtils.marshaller(GETTER.get(serde)) :
                new MarshallerAdaptor<>(serde);
    }

    public static MethodDescriptor.Marshaller<InputStream> forwardingMarshaller() {
        return FORWARDING_MARSHALLER;
    }
}
