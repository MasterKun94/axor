package io.axor.raft.behaviors;

import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.impl.ActorUnsafe;
import io.axor.raft.Peer;
import io.axor.raft.PeerInstance;
import io.axor.raft.PeerState;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftState;
import io.axor.raft.messages.PeerMessage;
import io.axor.raft.messages.PeerMessage.RequestVote;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class CandidateBehavior extends AbstractPeerBehavior {
    private static final Logger LOG = LoggerFactory.getLogger(CandidateBehavior.class);

    private final ScheduledFuture<?> candidateTimeoutFuture;
    private final long term;
    private final long txnId;
    private final int majority;
    private final Set<Peer> ackedPeer;
    private int grantedCnt = 1;
    private int ungrantedCnt = 0;

    protected CandidateBehavior(RaftContext raftContext) {
        super(raftContext);
        raftContext.changeSelfPeerState(PeerState.CANDIDATE);
        RaftState raftState = raftState();
        term = raftState.getCurrentTerm() + 1;
        txnId = raftContext.generateTxnId();
        majority = raftContext.getPeers().size() / 2 + 1;
        ackedPeer = new HashSet<>(raftContext.getPeers().size());
        raftState.setVotedFor(raftContext.getSelfPeer().peer());
        raftState.setCurrentTerm(term);
        for (PeerInstance peer : raftContext.getPeers()) {
            if (peer.isSelf()) {
                continue;
            }
            peer.peerRef().tell(new RequestVote(txnId, term, raftState.getCommitedId()), self());
        }
        long timeout = config().candidateTimeoutBase().toMillis();
        double ratio = timeout * config().candidateTimeoutRandomRatio();
        timeout += ThreadLocalRandom.current().nextLong((long) ratio);
        candidateTimeoutFuture = context().dispatcher().schedule(() -> {
            ActorUnsafe.signal(self(), new CandidateTimeoutSignal(term));
        }, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    protected Behavior<PeerMessage> onClientTxn(PeerMessage.ClientTxnReq msg) {
        return super.onClientTxn(msg);
    }

    @Override
    protected Behavior<PeerMessage> onLogAppend(PeerMessage.LogAppend msg) {
        if (msg.term() == raftState().getCurrentTerm()) {
            Peer leaderPeer = raftContext().getPeerOfSender().peer();
            raftState().setVotedFor(leaderPeer);
            raftState().setLeader(leaderPeer);
            candidateTimeoutFuture.cancel(false);
            return Behaviors.consumeBuffer(new FollowerBehavior(raftContext()))
                    .addMsg(msg, peerSender())
                    .toBehavior();
        } else {
            return super.onLogAppend(msg);
        }
    }

    @Override
    protected Behavior<PeerMessage> onLeaderHeartbeat(PeerMessage.LeaderHeartbeat msg) {
        if (msg.term() == raftState().getCurrentTerm()) {
            Peer leaderPeer = raftContext().getPeerOfSender().peer();
            raftState().setVotedFor(leaderPeer);
            raftState().setLeader(leaderPeer);
            candidateTimeoutFuture.cancel(false);
            return Behaviors.consumeBuffer(new FollowerBehavior(raftContext()))
                    .addMsg(msg, peerSender())
                    .toBehavior();
        } else {
            return super.onLeaderHeartbeat(msg);
        }
    }

    @Override
    protected Behavior<PeerMessage> onRequestVoteAck(PeerMessage.RequestVoteAck msg) {
        if (msg.txnId() == txnId) {
            RaftContext raftContext = raftContext();
            PeerInstance peerOfSender = raftContext.getPeerOfSender();
            if (!ackedPeer.add(peerOfSender.peer())) {
                LOG.warn("Already receive ack from {}, ignore", peerOfSender.peer());
                return Behaviors.same();
            }
            if (msg.voteGranted()) {
                LOG.info("{} from {} at term {}", msg, peerOfSender.peer(), term);
                grantedCnt++;
                if (grantedCnt >= majority) {
                    return new LeaderIdleBehavior(raftContext);
                }
            } else {
                LOG.warn("{} from {} at term {}", msg, peerOfSender.peer(), term);
                ungrantedCnt++;
                if (ungrantedCnt >= majority) {
                    return new FollowerBehavior(raftContext);
                }
            }
        }
        return Behaviors.unhandled();
    }

    @Override
    protected Behavior<PeerMessage> onSignal(Signal signal) {
        if (signal instanceof CandidateTimeoutSignal(var t) && t == term) {
            LOG.warn("Candidate timeout with term {}, restart candidate", term);
            return new CandidateBehavior(raftContext());
        }
        return super.onSignal(signal);
    }

    @Override
    protected void onBehaviorChanged() {
        candidateTimeoutFuture.cancel(false);
    }

    private record CandidateTimeoutSignal(long term) implements Signal {
    }
}
