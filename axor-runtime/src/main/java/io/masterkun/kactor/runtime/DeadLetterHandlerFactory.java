package io.masterkun.kactor.runtime;

public interface DeadLetterHandlerFactory {
    DeadLetterHandler create(StreamDefinition<?> remoteDef, StreamDefinition<?> selfDef);
}
