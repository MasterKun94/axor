package io.axor.runtime.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import io.axor.api.MessageUtils;
import io.axor.logging.Loggable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class ProtoPrinter implements MessageUtils.MsgPrinter {

    private static void formatTo(Object message, StringBuilder builder) {
        switch (message) {
            case null -> builder.append("null");
            case MessageOrBuilder proto -> {
                builder.append('(');
                boolean first = true;
                for (Descriptors.FieldDescriptor field : proto.getDescriptorForType().getFields()) {
                    Object value = proto.getField(field);
                    if (field.getJavaType() != Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                        if (value.equals(field.getDefaultValue())) {
                            continue;
                        }
                    } else if (value instanceof List<?> l) {
                        if (l.isEmpty()) {
                            continue;
                        }
                    } else if (value instanceof Map<?, ?> m) {
                        if (m.isEmpty()) {
                            continue;
                        }
                    } else if (value instanceof Message m) {
                        if (m.equals(m.getDefaultInstanceForType())) {
                            continue;
                        }
                    }
                    if (first) {
                        first = false;
                    } else {
                        builder.append(", ");
                    }
                    builder.append(field.getName()).append('=');
                    formatTo(value, builder);
                }
                builder.append(')');
            }
            case Iterable<?> iterable -> {
                boolean first = true;
                builder.append('[');
                for (Object o : iterable) {
                    if (first) {
                        first = false;
                    } else {
                        builder.append(", ");
                    }
                    if (o instanceof MapEntry<?, ?> e) {
                        formatTo(e.getValue(), builder.append(e.getKey()).append('='));
                    } else {
                        formatTo(o, builder);
                    }
                }
                builder.append(']');
            }
            case Map<?, ?> map -> {
                boolean first = true;
                builder.append('{');
                for (Map.Entry<?, ?> o : map.entrySet()) {
                    if (first) {
                        first = false;
                    } else {
                        builder.append(", ");
                    }
                    formatTo(o.getValue(), builder.append(o.getKey()).append('='));
                }
                builder.append('}');
            }
            case Loggable loggable -> loggable.formatTo(builder);
            default -> builder.append(message);
        }
    }

    @Override
    public Object loggable(Object msg) {
        return msg instanceof MessageOrBuilder mb ? new LoggableImpl(mb) : msg;
    }

    public record LoggableImpl(Object obj) implements Loggable {

        @Override
        public void formatTo(StringBuilder builder) {
            builder.append(obj.getClass().getSimpleName());
            ProtoPrinter.formatTo(obj, builder);
        }

        @Override
        public @NotNull String toString() {
            StringBuilder builder = new StringBuilder();
            formatTo(builder);
            return builder.toString();
        }
    }

}
