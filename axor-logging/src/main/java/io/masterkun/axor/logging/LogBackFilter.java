package io.masterkun.axor.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class LogBackFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        // 指定打印日志类

        if (event.getLoggerName().contains("io.masterkun.axor")) {
            return FilterReply.ACCEPT;
        } else {
            return event.getLevel().isGreaterOrEqual(Level.INFO) ?
                    FilterReply.ACCEPT : FilterReply.DENY;
        }
    }
}

