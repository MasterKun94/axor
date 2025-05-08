package io.axor.raft.messages;

import com.google.protobuf.ByteString;
import io.axor.raft.ClientTxnStatus;

public sealed interface ClientMessage {
    record ClientTxnRes(long txnId, ClientTxnStatus status,
                        ByteString data) implements ClientMessage {
        public ClientTxnRes(long txnId, ClientTxnStatus status) {
            this(txnId, status, ByteString.empty());
        }
    }

}
