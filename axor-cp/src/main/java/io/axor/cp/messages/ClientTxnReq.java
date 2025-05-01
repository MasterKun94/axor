package io.axor.cp.messages;

import com.google.protobuf.ByteString;

public record ClientTxnReq(long clientTxnId, ByteString data) implements ServiceMessage {
}
