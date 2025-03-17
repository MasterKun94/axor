package io.masterkun.kactor.exception;

import io.masterkun.kactor.runtime.MsgType;

/**
 * Exception thrown when a message type does not match the expected type.
 * This exception extends the {@link ActorException} and is used to indicate that an operation
 * attempted with a message of an unexpected type.
 */
public final class IllegalMsgTypeException extends ActorException {
    private final MsgType<?> expected;
    private final MsgType<?> actual;

    public IllegalMsgTypeException(MsgType<?> expected, MsgType<?> actual) {
        super("expected " + expected + " but got " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    public MsgType<?> getExpected() {
        return expected;
    }

    public MsgType<?> getActual() {
        return actual;
    }
}
