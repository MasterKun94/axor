package io.axor.commons.stream;

public record ErrorSignal(Throwable cause) implements EventFlow.Signal {
}
