package io.axor.raft.behaviors;

import com.google.protobuf.ByteString;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.Peer;
import io.axor.raft.PeerInstance;
import io.axor.raft.PeerState;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftState;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.PeerMessage;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static io.axor.api.MessageUtils.loggable;

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
        raftState.setVotedFor(raftContext.getSelfPeer());
        raftState.setCurrentTerm(term);
        for (PeerInstance peer : raftContext.getPeers()) {
            if (peer.isSelf()) {
                continue;
            }
            peer.peerRef().tell(peerMsg(PeerProto.RequestVote.newBuilder()
                    .setTxnId(txnId)
                    .setTerm(term)
                    .setLogEndId(raftContext.getLogEndId())
                    .build()), self());
        }
        long timeout = config().candidateTimeoutBase().toMillis();
        double ratio = timeout * config().candidateTimeoutRandomRatio();
        timeout += ThreadLocalRandom.current().nextLong((long) ratio);
        candidateTimeoutFuture = context().scheduler().scheduleSignal(
                () -> new CandidateTimeoutSignal(term), self(),
                timeout, TimeUnit.MILLISECONDS);
        LOG.info("Start candidate at term: {}", term);
    }

    @Override
    protected Behavior<PeerMessage> onClientTxnReq(PeerProto.ClientTxnReq msg) {
        clientSender().tell(clientMsg(PeerProto.ClientTxnRes.newBuilder()
                .setSeqId(txnId)
                .setStatus(PeerProto.ClientTxnRes.Status.NO_LEADER)
                .build()), self());
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onLogAppend(PeerProto.LogAppend msg) {
        if (msg.getTerm() == raftState().getCurrentTerm()) {
            PeerInstance leaderPeer = raftContext().getPeerOfSender();
            raftState().setVotedFor(leaderPeer);
            raftState().setLeader(leaderPeer);
            candidateTimeoutFuture.cancel(false);
            return Behaviors.consumeBuffer(new FollowerBehavior(raftContext()))
                    .addMsg(peerMsg(msg), peerSender())
                    .toBehavior();
        } else {
            return super.onLogAppend(msg);
        }
    }

    @Override
    protected Behavior<PeerMessage> onLeaderHeartbeat(PeerProto.LeaderHeartbeat msg) {
        if (msg.getTerm() == raftState().getCurrentTerm()) {
            PeerInstance leaderPeer = raftContext().getPeerOfSender();
            raftState().setVotedFor(leaderPeer);
            raftState().setLeader(leaderPeer);
            candidateTimeoutFuture.cancel(false);
            return Behaviors.consumeBuffer(new FollowerBehavior(raftContext()))
                    .addMsg(peerMsg(msg), peerSender())
                    .toBehavior();
        } else {
            return super.onLeaderHeartbeat(msg);
        }
    }

    @Override
    protected Behavior<PeerMessage> onRequestVoteAck(PeerProto.RequestVoteAck msg) {
        if (msg.getTxnId() == txnId) {
            RaftContext raftContext = raftContext();
            PeerInstance peerOfSender = raftContext.getPeerOfSender();
            if (!ackedPeer.add(peerOfSender.peer())) {
                LOG.warn("Already receive ack from {}, ignore", peerOfSender.peer());
                return Behaviors.same();
            }
            if (msg.getVoteGranted()) {
                LOG.info("{} from {} at term {}", loggable(msg), peerOfSender.peer(), term);
                grantedCnt++;
                if (grantedCnt >= majority) {
                    // 成为leader后加一条空日志，保证过去任期的日志提交（safety）
                    // 并且顺带告知follower自己是leader
                    return Behaviors.consumeBuffer(new LeaderIdleBehavior(raftContext))
                            .addMsg(peerMsg(PeerProto.ClientTxnReq.newBuilder()
                                    .setSeqId(raftContext.generateTxnId())
                                    // TODO self clientId
                                    .setClientId(raftContext.getSelfPeer().peer().id())
                                    .setData(ByteString.empty())
                                    .setControlFlag(PeerProto.ControlFlag.IGNORE)
                                    .build()), ActorRef.noSender())
                            .toBehavior();
                }
            } else {
                LOG.warn("{} from {} at term {}", loggable(msg), peerOfSender.peer(), term);
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
