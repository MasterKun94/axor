package io.axor.cp.messages;

public sealed interface ServiceMessage extends RaftMessage
        permits ClientTxnReq, LogFetch, LogFetchRes {
}
