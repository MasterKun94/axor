package io.axor.runtime;

public interface DeadLetterHandlerFactory {
    DeadLetterHandler create(StreamDefinition<?> remoteDef, StreamDefinition<?> selfDef);
}
