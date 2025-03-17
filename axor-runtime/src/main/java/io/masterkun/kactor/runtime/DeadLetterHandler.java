package io.masterkun.kactor.runtime;

public interface DeadLetterHandler {
    void handle(Object msg);
}
