package io.axor.raft.behaviors;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.PeerInstance;
import io.axor.raft.RaftConfig;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftState;
import io.axor.raft.logging.AsyncRaftLogging;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.ClientMessage;
import io.axor.raft.proto.PeerProto.CommitResult;
import io.axor.raft.proto.PeerProto.LogAppend;
import io.axor.raft.proto.PeerProto.LogAppendAck;
import io.axor.raft.proto.PeerProto.LogEntry;
import io.axor.raft.proto.PeerProto.LogId;
import io.axor.raft.proto.PeerProto.PeerMessage;
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
                .append(append.getEntriesList(), context().dispatcher().newPromise())
                .map(res -> {
                    RaftState raftState = raftState();
                    raftState.setCommitedId(res.getCommited());
                    raftState.setUncommitedId(res.getUncommitedList());
                    return res;
                });
    }

    protected EventStage<CommitResult> logCommit(LogId commitId) {
        return raftLogging()
                .commit(commitId, context().dispatcher().newPromise())
                .map(res -> {
                    RaftState raftState = raftState();
                    raftState.setCommitedId(res.getCommited());
                    raftState.setUncommitedId(res.getUncommitedList());
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

    protected PeerMessage peerMsg(PeerProto.ClientTxnReq msg) {
        return PeerMessage.newBuilder().setClientTxnReq(msg).build();
    }

    protected PeerMessage peerMsg(PeerProto.LogAppend msg) {
        return PeerMessage.newBuilder().setLogAppend(msg).build();
    }

    protected PeerMessage peerMsg(LogAppendAck msg) {
        return PeerMessage.newBuilder().setLogAppendAck(msg).build();
    }

    protected PeerMessage peerMsg(PeerProto.LeaderHeartbeat msg) {
        return PeerMessage.newBuilder().setLeaderHeartbeat(msg).build();
    }

    protected PeerMessage peerMsg(PeerProto.LogFetch msg) {
        return PeerMessage.newBuilder().setLogFetch(msg).build();
    }

    protected PeerMessage peerMsg(PeerProto.LogFetchRes msg) {
        return PeerMessage.newBuilder().setLogFetchRes(msg).build();
    }

    protected PeerMessage peerMsg(PeerProto.RequestVote msg) {
        return PeerMessage.newBuilder().setRequestVote(msg).build();
    }

    protected PeerMessage peerMsg(PeerProto.RequestVoteAck msg) {
        return PeerMessage.newBuilder().setRequestVoteAck(msg).build();
    }

    protected ClientMessage clientMsg(PeerProto.ClientTxnRes msg) {
        return ClientMessage.newBuilder().setClientTxnRes(msg).build();
    }

    protected boolean isAppendSuccess(AppendResult.Status status) {
        return status == AppendResult.Status.SUCCESS || status == AppendResult.Status.NO_ACTION;
    }

    protected boolean isCommitSuccess(CommitResult.Status status) {
        return status == CommitResult.Status.SUCCESS || status == CommitResult.Status.NO_ACTION;
    }

    @Override
    public final Behavior<PeerMessage> onReceive(ActorContext<PeerMessage> context,
                                                 PeerMessage message) {
        Behavior<PeerMessage> behavior = switch (message.getMsgCase()) {
            case CLIENTTXNREQ -> onClientTxnReq(message.getClientTxnReq());
            case LOGAPPEND -> onLogAppend(message.getLogAppend());
            case LOGAPPENDACK -> onLogAppendAck(message.getLogAppendAck());
            case LEADERHEARTBEAT -> onLeaderHeartbeat(message.getLeaderHeartbeat());
            case LOGFETCH -> onLogFetch(message.getLogFetch());
            case LOGFETCHRES -> onLogFetchRes(message.getLogFetchRes());
            case REQUESTVOTE -> onRequestVote(message.getRequestVote());
            case REQUESTVOTEACK -> onRequestVoteAck(message.getRequestVoteAck());
            default -> throw new IllegalStateException("illegal msg type: " + message.getMsgCase());
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

    protected final Behavior<PeerMessage> onLogFetch(PeerProto.LogFetch msg) {
        int entryLimit = Math.min(msg.getLimit(), config().logFetchEntryLimit());
        int bytesLimit = config().logFetchBytesLimit().toInt();
        ActorRef<PeerMessage> sender = peerSender();
        raftLogging()
                .read(msg.getStartAt(), true, false, entryLimit, bytesLimit,
                        context().dispatcher().newPromise())
                .observe(l -> {
                    var res = PeerProto.LogFetchRes.newBuilder()
                            .setTxnId(msg.getTxnId())
                            .setSuccess(true)
                            .addAllEntries(l)
                            .setLeaderCommited(raftState().getCommitedId())
                            .build();
                    sender.tell(peerMsg(res), self());
                }, e -> {
                    LOG.error("LogFetch failure", e);
                    var res = PeerProto.LogFetchRes.newBuilder()
                            .setTxnId(msg.getTxnId())
                            .setSuccess(false)
                            .setErrMsg(e.toString())
                            .setLeaderCommited(raftState().getCommitedId())
                            .build();
                    sender.tell(peerMsg(res), self());
                });
        return Behaviors.same();
    }

    protected Behavior<PeerMessage> onClientTxnReq(PeerProto.ClientTxnReq msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onLogAppend(LogAppend msg) {
        long currentTerm = raftState().getCurrentTerm();
        ActorRef<PeerMessage> sender = peerSender();
        if (msg.getTerm() < currentTerm) {
            // 返回自己的当前Term，提示Leader更新
            LogAppendAck ack = LogAppendAck.newBuilder()
                    .setTxnId(msg.getTxnId())
                    .setTerm(currentTerm)
                    .setResult(AppendResult.newBuilder()
                            .setStatus(AppendResult.Status.TERM_DENY)
                            .setCommited(raftState().getCommitedId())
                            .addAllUncommited(raftState().getUncommitedId()))
                    .build();
            sender.tell(peerMsg(ack), self());
            return Behaviors.same();
        }
        updateLeader(msg.getTerm());
        if (msg.getTerm() > currentTerm) {
            return Behaviors.consumeBuffer(new FollowerBehavior(raftContext()))
                    .addMsg(peerMsg(msg), sender)
                    .toBehavior();
        }
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onLogAppendAck(LogAppendAck msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onLeaderHeartbeat(PeerProto.LeaderHeartbeat msg) {
        RaftState raftState = raftState();
        long currentTerm = raftState.getCurrentTerm();
        ActorRef<PeerMessage> sender = peerSender();
        if (msg.getTerm() < currentTerm) {
            // TODO 返回自己的当前Term，提示Leader更新
            return Behaviors.same();
        }
        updateLeader(msg.getTerm());
        if (msg.getTerm() > currentTerm) {
            return Behaviors.consumeBuffer(new FollowerBehavior(raftContext()))
                    .addMsg(peerMsg(msg), sender)
                    .toBehavior();
        }
        return Behaviors.unhandled();
    }

    protected void updateLeader(long term) {
        RaftState raftState = raftState();
        long currentTerm = raftState.getCurrentTerm();
        PeerInstance leaderPeer = raftContext().getPeerOfSender();
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

    protected Behavior<PeerMessage> onLogFetchRes(PeerProto.LogFetchRes msg) {
        return Behaviors.unhandled();
    }

    protected Behavior<PeerMessage> onRequestVote(PeerProto.RequestVote msg) {
        RaftState raftState = raftState();
        long currentTerm = raftState.getCurrentTerm();
        PeerInstance peerOfSender = raftContext().getPeerOfSender();
        PeerInstance votedFor = raftState.getVotedFor();
        if (msg.getTerm() < currentTerm) {
            // 如果请求中的任期号小于当前服务器的任期，则拒绝
            peerSender().tell(peerMsg(PeerProto.RequestVoteAck.newBuilder()
                    .setTxnId(msg.getTxnId())
                    .setTerm(currentTerm)
                    .setVoteGranted(false).build()), self());
            return Behaviors.same();
        }
        Behavior<PeerMessage> behavior;
        boolean voteGranted;
        if (msg.getTerm() > currentTerm) {
            // 如果请求中的任期号大于当前服务器的任期，则更新自己的任期，并转换为Follower
            raftState.setCurrentTerm(msg.getTerm());
            behavior = new FollowerBehavior(raftContext);
            if (msg.getLogEndId().getIndex() < raftContext().getLogEndId().getIndex()) {
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
            } else if (msg.getLogEndId().getIndex() < raftContext().getLogEndId().getIndex()) {
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
        peerSender().tell(peerMsg(PeerProto.RequestVoteAck.newBuilder()
                .setTxnId(msg.getTxnId())
                .setTerm(currentTerm)
                .setVoteGranted(voteGranted).build()), self());
        return behavior;
    }

    protected Behavior<PeerMessage> onRequestVoteAck(PeerProto.RequestVoteAck msg) {
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
