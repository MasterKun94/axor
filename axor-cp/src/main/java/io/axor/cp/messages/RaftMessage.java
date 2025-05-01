package io.axor.cp.messages;


public sealed interface RaftMessage
        permits NodeMessage, ServiceMessage {
}
