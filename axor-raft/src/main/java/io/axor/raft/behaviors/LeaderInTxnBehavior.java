package io.axor.raft.behaviors;

import com.google.protobuf.ByteString;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.impl.ActorUnsafe;
import io.axor.raft.AppendStatus;
import io.axor.raft.ClientTxnStatus;
import io.axor.raft.CommitStatus;
import io.axor.raft.LogId;
import io.axor.raft.Peer;
import io.axor.raft.PeerInstance;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftContext.FollowerState;
import io.axor.raft.messages.ClientMessage.ClientTxnRes;
import io.axor.raft.messages.PeerMessage;
import io.axor.raft.messages.PeerMessage.LeaderHeartbeat;
import io.axor.raft.messages.PeerMessage.LogAppend;
import io.axor.raft.messages.PeerMessage.LogAppendAck;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LeaderInTxnBehavior extends AbstractLeaderBehavior {
    private static final Logger LOG = LoggerFactory.getLogger(LeaderInTxnBehavior.class);

    private final long txnId;
    private final long term;
    private final List<RaftContext.ClientTxn> clientTxnList;
    private final ScheduledFuture<?> timeoutFuture;
    private final int majorityCount;
    private final LogId commitedId;
    private final Set<Peer> ackedPeer;
    private int successCnt;
    private int failureCnt;
    private Boolean leaderSuccess;
    private boolean txnFinished = false;

    protected LeaderInTxnBehavior(RaftContext raftContext) {
        super(raftContext);
        ackedPeer = new HashSet<>(raftContext.getPeers().size());
        majorityCount = raftContext.getPeers().size() / 2 + 1;

        txnId = raftContext().generateTxnId();
        RaftContext.TxnContext txnContext = leaderContext().prepareForTxn(txnId);
        LogAppend append = txnContext.append();
        commitedId = append.entries().getLast().id();
        clientTxnList = txnContext.clientTxnList();
        term = append.term();
        // local append entries
        logAppend(append).observe((res, e) -> {
            AppendStatus status;
            if (e != null) {
                LOG.error("LogAppend[txnId={}, commitedId={}, peer={}] error",
                        txnId, commitedId, raftContext().getSelfPeer().peer(), e);
                status = AppendStatus.SYSTEM_ERROR;
            } else {
                status = res.status();
            }
            if (!status.isSuccess()) {
                LOG.error("LogAppend[txnId={}, commitedId={}, peer={}] failure with status {}",
                        txnId, commitedId, raftContext().getSelfPeer().peer(), status);
                context().system()
                        .systemFailure(new RuntimeException("Leader local log append failure"));
            }
            LogAppendAck ack = new LogAppendAck(txnId, term, status,
                    raftState().getCommitedId(), raftState().getUncommitedId());
            ActorUnsafe.tellInline(self(), ack, self());
        });
        // append entries to peer
        Map<Peer, FollowerState> followerStates = leaderContext().getFollowerStates();
        for (PeerInstance peer : raftContext().getPeers()) {
            if (peer.isSelf()) {
                continue;
            }
            FollowerState followerState = followerStates.get(peer.peer());
            followerState.setLatestTxnId(txnId);
            LogId followerLogEndId = followerState.getLogEndId();
            LogId appendFirstId = append.entries().getFirst().id();
            if (followerLogEndId == null || followerLogEndId.index() == appendFirstId.index() - 1) {
                peer.peerRef().tell(append, self());
            } else {
                logReadForSync(followerState.getCommited(), followerState.getUncommited())
                        .observe((l, e) -> {
                            if (e != null) {
                                LOG.error("Read for sync error, Follower {} state suspicious",
                                        peer.peer(), e);
                                return;
                            }
                            var msg = new LogAppend(txnId, term, l, raftState().getCommitedId());
                            peer.peerRef().tell(msg, self());
                        });
            }
        }
        // schedule timeout
        Duration timeout = config().logAppendTimeout();
        timeoutFuture = context().scheduler().schedule(() -> {
            ActorUnsafe.signalInline(self(), new TimeoutSignal(txnId));
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected Behavior<PeerMessage> onClientTxn(PeerMessage.ClientTxnReq msg) {
        leaderContext().bufferClientCtx(msg);
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onLogAppendAck(LogAppendAck msg) {
        if (msg.txnId() != txnId) {
            return Behaviors.unhandled();
        }
        RaftContext raftContext = raftContext();
        PeerInstance peerOfSender = raftContext.getPeerOfSender();
        Peer peer = peerOfSender.peer();
        FollowerState followerState = leaderContext().getFollowerStates().get(peer);
        assert followerState.getLatestTxnId() == txnId;
        followerState.setCommited(msg.commited());
        followerState.setUncommited(msg.uncommited());

        if (!peerOfSender.isSelf()) {
            // follower存在延迟，还要继续追加写数据
            if ((msg.success() || msg.status() == AppendStatus.INDEX_EXCEEDED) &&
                msg.logEndId().index() < commitedId.index()) {
                logReadForSync(followerState.getCommited(), followerState.getUncommited())
                        .observe((l, e) -> {
                            if (e != null) {
                                LOG.error("Read for sync error, {} state suspicious", peer, e);
                                return;
                            }
                            var m = new LogAppend(txnId, term, l, raftState().getCommitedId());
                            peerOfSender.peerRef().tell(m, self());
                        });
                return Behaviors.same();
            }
        }
        // 已经收到ack
        if (!ackedPeer.add(peer)) {
            LOG.warn("Already receive ack from {}, ignore", peer);
            return Behaviors.same();
        }

        if (msg.success()) {
            assert msg.logEndId().index() < commitedId.index();
            successCnt++;
            if (peerOfSender.isSelf()) {
                leaderSuccess = true;
            }
            // leader成功并且总成功数量达到多数，执行本地commit
            if (leaderSuccess != null && leaderSuccess && successCnt >= majorityCount) {
                timeoutFuture.cancel(false);
                logCommit(commitedId).observe((s, e) -> {
                    CommitSignal signal;
                    if (e != null) {
                        LOG.error("LogCommit[txnId={}, commitedId={}, peer={}] error",
                                txnId, commitedId, raftContext.getSelfPeer().peer(), e);
                        signal = new CommitSignal(txnId, CommitStatus.SYSTEM_ERROR);
                    } else {
                        signal = new CommitSignal(txnId, s.status());
                    }
                    ActorUnsafe.signalInline(self(), signal);
                });
                return Behaviors.same();
            }
        } else {
            failureCnt++;
            if (peerOfSender.isSelf()) {
                leaderSuccess = false;
            }
            // leader失败或者总失败数量达到多数
            if ((leaderSuccess != null && !leaderSuccess) || failureCnt >= majorityCount) {
                return finishTxnAndReturn(ClientTxnStatus.APPEND_FAILURE, ByteString.empty());
            }
        }
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onSignal(Signal signal) {
        if (signal instanceof CommitSignal(var id, var status) && id == txnId) {
            if (status.isSuccess()) {
                long term = raftState().getCurrentTerm();
                LogId commitedId = raftState().getCommitedId();
                LeaderHeartbeat heartbeat = new LeaderHeartbeat(term, commitedId);
                for (PeerInstance peer : raftContext().getPeers()) {
                    if (peer.isSelf()) {
                        continue;
                    }
                    peer.peerRef().tell(heartbeat, self());
                }
                return finishTxnAndReturn(ClientTxnStatus.SUCCESS, ByteString.empty());
            } else {
                Behavior<PeerMessage> behavior = finishTxnAndReturn(ClientTxnStatus.COMMIT_FAILURE,
                        ByteString.copyFromUtf8(status.name()));
                LOG.error("LogCommit[txnId={}, commitedId={}, peer={}] failure with status {}",
                        txnId, commitedId, raftContext().getSelfPeer().peer(), status);
                context().system()
                        .systemFailure(new RuntimeException("Leader local log commit failure"));
                return behavior;
            }
        }
        if (signal instanceof TimeoutSignal(var id) && id == txnId) {
            return finishTxnAndReturn(ClientTxnStatus.APPEND_TIMEOUT, ByteString.empty());
        }
        return Behaviors.unhandled();
    }

    @Override
    protected void onBehaviorChanged() {
        finishTxn(ClientTxnStatus.NO_LEADER, ByteString.empty());
    }

    private void finishTxn(ClientTxnStatus resStatus, ByteString msg) {
        if (txnFinished) {
            return;
        }
        timeoutFuture.cancel(false);
        for (RaftContext.ClientTxn clientTxn : clientTxnList) {
            var reply = new ClientTxnRes(clientTxn.txnId(), resStatus, msg);
            clientTxn.clientRef().tell(reply, self());
        }
        txnFinished = true;
    }

    private Behavior<PeerMessage> finishTxnAndReturn(ClientTxnStatus resStatus, ByteString msg) {
        finishTxn(resStatus, msg);
        if (leaderContext().bufferIsEmpty()) {
            return new LeaderIdleBehavior(raftContext());
        } else {
            return new LeaderInTxnBehavior(raftContext());
        }
    }

    private record TimeoutSignal(long txnId) implements Signal {
    }

    private record CommitSignal(long txnId, CommitStatus commitStatus) implements Signal {
    }
}
