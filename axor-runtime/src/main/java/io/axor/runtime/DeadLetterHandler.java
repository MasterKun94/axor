package io.axor.runtime;

public interface DeadLetterHandler {
    void handle(Object msg);
}
