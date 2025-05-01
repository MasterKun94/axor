package io.axor.cp.messages;

public record ClientTxnRes(long clientTxnId, boolean success,
                           String message) implements ClientMessage {
    // TODO
}
