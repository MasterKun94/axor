package io.axor.exception;

public abstract sealed class ActorException extends Exception
        permits ActorNotFoundException, IllegalMsgTypeException {
    public ActorException(String message) {
        super(message);
    }
}
