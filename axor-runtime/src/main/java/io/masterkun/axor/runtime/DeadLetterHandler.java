package io.masterkun.axor.runtime;

public interface DeadLetterHandler {
    void handle(Object msg);
}
