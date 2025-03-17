package io.masterkun.kactor.commons;

public class RuntimeUtil {
    /**
     * Appends a simple string representation of the given throwable to the provided StringBuilder.
     * The representation includes the simple name of the throwable's class, its localized message (if any),
     * and its cause (if any), recursively formatted in the same way.
     *
     * @param throwable the Throwable to be converted to a simple string
     * @param builder   the StringBuilder to which the simple string representation will be appended
     */
    private static void toSimpleString(Throwable throwable, StringBuilder builder) {
        builder.append(throwable.getClass().getSimpleName());
        boolean first = true;
        if (throwable.getLocalizedMessage() != null) {
            builder.append('[');
            first = false;
            builder.append("msg='").append(throwable.getLocalizedMessage()).append('\'');
        }
        if (throwable.getCause() != null) {
            if (!first) {
                builder.append(", ");
            } else {
                builder.append('[');
                first = false;
            }
            builder.append("cause='");
            toSimpleString(throwable.getCause(), builder);
            builder.append("'");
        }
        if (!first) {
            builder.append(']');
        }
    }

    public static String toSimpleString(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        toSimpleString(throwable, builder);
        return builder.toString();
    }
}
