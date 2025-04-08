package io.axor.api;

/**
 * A record representing a message that could not be delivered to its intended recipient, often due
 * to the recipient being unavailable or unable to process the message. This is typically used in
 * actor-based systems to handle undeliverable messages.
 *
 * @param receiver The {@code ActorAddress} of the intended recipient of the message.
 * @param sender   The {@code ActorRef} of the sender of the message.
 * @param message  The actual message that failed to be delivered.
 */
public record DeadLetter(ActorAddress receiver, ActorRef<?> sender, Object message) {
}
