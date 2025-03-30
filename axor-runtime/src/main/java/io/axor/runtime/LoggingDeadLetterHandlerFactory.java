package io.axor.runtime;

import org.slf4j.Logger;

public class LoggingDeadLetterHandlerFactory implements DeadLetterHandlerFactory {
    private final Logger logger;

    public LoggingDeadLetterHandlerFactory(Logger logger) {
        this.logger = logger;
    }

    @Override
    public DeadLetterHandler create(StreamDefinition<?> remoteDef, StreamDefinition<?> selfDef) {
        return msg -> logger.warn("DeadLetter[{}] from {} to {}", msg, remoteDef, selfDef);
    }
}
