package io.masterkun.axor.logging;

import org.apache.logging.log4j.util.StringBuilderFormattable;

public interface Loggable extends StringBuilderFormattable {
    void formatTo(StringBuilder builder);
}
