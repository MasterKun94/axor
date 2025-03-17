package io.masterkun.kactor.api;

public record DeadLetter(ActorAddress receiver, ActorRef<?> sender, Object message) {
}
