package io.axor.api;

import java.lang.reflect.InvocationTargetException;

public class MessageUtils {
    private static final MsgPrinter PROTO_PRINTER;

    static {
        MsgPrinter printer;
        try {
            Class<?> cls = Class.forName("io.axor.runtime.serde.protobuf.ProtoPrinter");
            printer = (MsgPrinter) cls.getConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InstantiationException | InvocationTargetException e) {
            printer = null;
        }
        PROTO_PRINTER = printer;
    }

    public static Object loggable(Object obj) {
        try {
            return PROTO_PRINTER == null ? obj : PROTO_PRINTER.loggable(obj);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public interface MsgPrinter {
        Object loggable(Object obj);
    }
}
