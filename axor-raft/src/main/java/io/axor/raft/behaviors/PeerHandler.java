package io.axor.raft.behaviors;

import io.axor.api.Behavior;
import io.axor.raft.messages.PeerMessage;
import io.axor.raft.messages.PeerMessage.ClientTxnReq;
import io.axor.raft.messages.PeerMessage.LeaderHeartbeat;
import io.axor.raft.messages.PeerMessage.LogAppend;
import io.axor.raft.messages.PeerMessage.LogAppendAck;
import io.axor.raft.messages.PeerMessage.LogFetch;
import io.axor.raft.messages.PeerMessage.LogFetchRes;
import io.axor.raft.messages.PeerMessage.RequestVote;
import io.axor.raft.messages.PeerMessage.RequestVoteAck;
import io.axor.runtime.Signal;

public abstract class PeerHandler {

    protected abstract Behavior<PeerMessage> onLogFetch(LogFetch msg);

    protected abstract Behavior<PeerMessage> onClientTxn(ClientTxnReq msg);

    protected abstract Behavior<PeerMessage> onLogAppend(LogAppend msg);

    protected abstract Behavior<PeerMessage> onLogAppendAck(LogAppendAck msg);

    protected abstract Behavior<PeerMessage> onLeaderHeartbeat(LeaderHeartbeat msg);

    protected abstract Behavior<PeerMessage> onLogFetchRes(LogFetchRes msg);

    protected abstract Behavior<PeerMessage> onRequestVote(RequestVote msg);

    protected abstract Behavior<PeerMessage> onRequestVoteAck(RequestVoteAck msg);

    protected abstract Behavior<PeerMessage> onSignal(Signal signal);
}
