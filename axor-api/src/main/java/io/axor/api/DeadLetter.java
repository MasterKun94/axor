package io.axor.api;

public record DeadLetter(ActorAddress receiver, ActorRef<?> sender, Object message) {
}
