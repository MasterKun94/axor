package io.axor.raft.behaviors;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.AppendStatus;
import io.axor.raft.CommitStatus;
import io.axor.raft.LogEntry;
import io.axor.raft.LogId;
import io.axor.raft.Peer;
import io.axor.raft.PeerInstance;
import io.axor.raft.RaftConfig;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftState;
import io.axor.raft.logging.AppendResult;
import io.axor.raft.logging.AsyncRaftLogging;
import io.axor.raft.logging.CommitResult;
import io.axor.raft.messages.ClientMessage;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractPeerBehavior implements Behavior<PeerMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPeerBehavior.class);

    private final RaftContext raftContext;

    protected AbstractPeerBehavior(RaftContext raftContext) {
        this.raftContext = raftContext;
    }

    protected ActorContext<PeerMessage> context() {
        return raftContext.getContext();
    }

    protected RaftConfig config() {
        return raftContext.getConfig();
    }

    protected RaftContext raftContext() {
        return raftContext;
    }

    protected RaftState raftState() {
        return raftContext.getRaftState();
    }

    protected ActorRef<PeerMessage> self() {
        return context().self();
    }

    protected ActorRef<PeerMessage> peerSender() {
        return context().sender(PeerMessage.class);
    }

    protected ActorRef<ClientMessage> clientSender() {
        return context().sender(ClientMessage.class);
    }

    protected AsyncRaftLogging raftLogging() {
        return raftContext.getRaftLogging();
    }

    protected EventStage<AppendResult> logAppend(LogAppend append) {
        return raftLogging()
                .append(append.entries(), context().dispatcher().newPromise())
                .map(res -> {
                    RaftState raftState = raftState();
                    raftState.setUncommitedId(res.uncommited());
                    return res;
                });
    }

    protected EventStage<CommitResult> logCommit(LogId commitId) {
        if (raftState().getCommitedId().equals(commitId)) {
            var res = new CommitResult(CommitStatus.NO_ACTION, commitId);
            return EventStage.succeed(res, context().dispatcher());
        }
        return raftLogging()
                .commit(commitId, context().dispatcher().newPromise())
                .map(res -> {
                    RaftState raftState = raftState();
                    raftState.setCommitedId(res.commited());
                    return res;
                });
    }

    protected EventStage<List<LogEntry>> logReadForSync(LogId commited, List<LogId> uncommited) {
        int entryLimit = config().logAppendEntryLimit();
        var bytesLimit = config().logAppendBytesLimit().toInt();
        EventPromise<List<LogEntry>> promise = context().dispatcher().newPromise();
        return raftLogging().readForSync(commited, uncommited, entryLimit, bytesLimit, promise);
    }

    protected boolean isBehaviorDifferent(Behavior<?> behavior) {
        return !behavior.equals(Behaviors.same()) &&
               !behavior.equals(Behaviors.unhandled()) &&
               !behavior.equals(this);
    }

    @Override
    public final Behavior<PeerMessage> onReceive(ActorContext<PeerMessage> context,
                                                 PeerMessage message) {
        Behavior<PeerMessage> behavior = switch (message) {
            case ClientTxnReq msg -> onClientTxn(msg);
            case LogAppend msg -> onLogAppend(msg);
            case LogAppendAck msg -> onLogAppendAck(msg);
            case LeaderHeartbeat msg -> onLeaderHeartbeat(msg);
            case LogFetch msg -> onLogFetch(msg);
            case LogFetchRes msg -> onLogFetchRes(msg);
            case RequestVote msg -> onRequestVote(msg);
            case RequestVoteAck msg -> onRequestVoteAck(msg);
        };
        checkBehaviorChanged(behavior);
        return behavior;
    }

    @Override
    public final Behavior<PeerMessage> onSignal(ActorContext<PeerMessage> context, Signal signal) {
        Behavior<PeerMessage> behavior = onSignal(signal);
        checkBehaviorChanged(behavior);
        return behavior;
    }

    protected final Behavior<PeerMessage> onLogFetch(LogFetch msg) {
        int entryLimit = Math.min(msg.limit(), config().logFetchEntryLimit());
        int bytesLimit = config().logFetchBytesLimit().toInt();
        ActorRef<PeerMessage> sender = peerSender();
        raftLogging()
                .read(msg.startAt(), true, false, entryLimit, bytesLimit,
                        context().dispatcher().newPromise())
                .observe(l -> {
                    var res = new LogFetchRes(msg.txnId(), l, raftState().getCommitedId());
                    sender.tell(res, self());
                }, e -> {
                    LOG.error("LogFetch failure", e);
                    var res = new LogFetchRes(msg.txnId(), e.toString(),
                            raftState().getCommitedId());
                    sender.tell(res, self());
                });
        return Behaviors.same();
    }

    protected Behavior<PeerMessage> onClientTxn(ClientTxnReq msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onLogAppend(LogAppend msg) {
        long currentTerm = raftState().getCurrentTerm();
        ActorRef<PeerMessage> sender = peerSender();
        if (msg.term() < currentTerm) {
            // 返回自己的当前Term，提示Leader更新
            LogAppendAck ack = new LogAppendAck(msg.txnId(), currentTerm, AppendStatus.TERM_DENY,
                    raftState().getCommitedId(), raftState().getUncommitedId());
            sender.tell(ack, self());
            return Behaviors.same();
        }
        updateLeader(msg, msg.term());
        if (msg.term() > currentTerm) {
            return Behaviors.consumeBuffer(new FollowerBehavior(raftContext()))
                    .addMsg(msg, sender)
                    .toBehavior();
        }
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onLogAppendAck(LogAppendAck msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onLeaderHeartbeat(LeaderHeartbeat msg) {
        RaftState raftState = raftState();
        long currentTerm = raftState.getCurrentTerm();
        ActorRef<PeerMessage> sender = peerSender();
        if (msg.term() < currentTerm) {
            // TODO 返回自己的当前Term，提示Leader更新
            return Behaviors.same();
        }
        updateLeader(msg, msg.term());
        if (msg.term() > currentTerm) {
            return Behaviors.consumeBuffer(new FollowerBehavior(raftContext()))
                    .addMsg(msg, sender)
                    .toBehavior();
        }
        return Behaviors.unhandled();
    }

    protected void updateLeader(PeerMessage leaderMsg, long term) {
        RaftState raftState = raftState();
        long currentTerm = raftState.getCurrentTerm();
        PeerInstance peerOfSender = raftContext().getPeerOfSender();
        Peer leaderPeer = peerOfSender.peer();
        if (term > currentTerm) {
            raftState.setCurrentTerm(term);
            raftState.setVotedFor(leaderPeer);
            raftState.setLeader(leaderPeer);
        } else if (term == currentTerm) {
            if (raftState.getLeader() == null) {
                raftState.setLeader(leaderPeer);
            } else if (!raftState.getLeader().equals(leaderPeer)) {
                throw new IllegalStateException("multiple leader with same term");
            }
            if (raftState.getVotedFor() == null) {
                raftState.setVotedFor(leaderPeer);
            }
        }
    }

    protected Behavior<PeerMessage> onLogFetchRes(LogFetchRes msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onRequestVote(RequestVote msg) {
        RaftState raftState = raftState();
        long currentTerm = raftState.getCurrentTerm();
        Peer peerOfSender = raftContext().getPeerOfSender().peer();
        Peer votedFor = raftState.getVotedFor();
        if (msg.term() < currentTerm) {
            // 如果请求中的任期号小于当前服务器的任期，则拒绝
            peerSender().tell(new RequestVoteAck(msg.txnId(), currentTerm, false), self());
            return Behaviors.same();
        }
        Behavior<PeerMessage> behavior;
        boolean voteGranted;
        int compare = msg.logEndId().compareTo(raftContext().getLogEndId());
        if (msg.term() > currentTerm) {
            // 如果请求中的任期号大于当前服务器的任期，则更新自己的任期，并转换为Follower
            raftState.setCurrentTerm(msg.term());
            behavior = new FollowerBehavior(raftContext);
            if (compare < 0) {
                // 候选人的日志不够新，则拒绝
                voteGranted = false;
            } else {
                // 否则，授予投票
                voteGranted = true;
                raftState.setVotedFor(peerOfSender);
            }
        } else {
            if (votedFor != null && !votedFor.equals(peerOfSender)) {
                // 如果任期相同，但当前服务器已经投票给另一个候选人，则拒绝
                voteGranted = false;
                behavior = Behaviors.same();
            } else if (compare < 0) {
                // 候选人的日志不够新，则拒绝
                voteGranted = false;
                behavior = Behaviors.same();
            } else {
                // 否则，授予投票，并转换为Follower
                voteGranted = true;
                raftState.setVotedFor(peerOfSender);
                behavior = new FollowerBehavior(raftContext);
            }
        }
        peerSender().tell(new RequestVoteAck(msg.txnId(), currentTerm, voteGranted), self());
        return behavior;
    }

    protected Behavior<PeerMessage> onRequestVoteAck(RequestVoteAck msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onSignal(Signal signal) {
        return Behaviors.unhandled();
    }

    private void checkBehaviorChanged(Behavior<?> behavior) {
        if (isBehaviorDifferent(behavior)) {
            onBehaviorChanged();
            LOG.debug("Behavior changed from {} to {}", this, behavior);
        }
    }

    protected void onBehaviorChanged() {
    }
}
