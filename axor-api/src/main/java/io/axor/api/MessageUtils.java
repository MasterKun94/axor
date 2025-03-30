package io.axor.api;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MessageUtils {
    private static final MethodHandle PROTO_TOSTRING;

    static {
        MethodHandle handle;
        try {
            Class<?> cls = Class.forName("io.axor.runtime.serde.protobuf.ProtobufUtil");
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            handle = lookup.findStatic(cls, "loggable", MethodType.methodType(Object.class,
                    Object.class));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            handle = null;
        }
        PROTO_TOSTRING = handle;
    }

    public static Object loggable(Object obj) {
        try {
            return PROTO_TOSTRING == null ? obj : PROTO_TOSTRING.invoke(obj);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
