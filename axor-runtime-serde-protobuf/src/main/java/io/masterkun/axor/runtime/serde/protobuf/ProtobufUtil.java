package io.masterkun.axor.runtime.serde.protobuf;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;

public class ProtobufUtil {
    private static final TextFormat.Printer PRINTER = TextFormat.printer().emittingSingleLine(true);

    public static String toString(MessageOrBuilder msg) {
        return PRINTER.printToString(msg);
    }

    public static Object loggable(Object msg) {
        return msg instanceof MessageOrBuilder mb ? toString(mb) : msg;
    }
}
