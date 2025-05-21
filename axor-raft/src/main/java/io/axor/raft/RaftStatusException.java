package io.axor.raft;

import io.axor.raft.proto.PeerProto;

public class RaftStatusException extends RuntimeException {
    private final PeerProto.MediatorMessage.Status status;

    public RaftStatusException(PeerProto.MediatorMessage.Status status, String message) {
        super(message == null || message.isEmpty() ?
                "failure status: " + status :
                "failure status: " + status + ", message: " + message);
        this.status = status;
    }

    public PeerProto.MediatorMessage.Status getStatus() {
        return status;
    }
}
