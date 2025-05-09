package io.axor.raft.behaviors;

import com.google.protobuf.ByteString;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.impl.ActorUnsafe;
import io.axor.raft.Peer;
import io.axor.raft.PeerInstance;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftContext.FollowerState;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.ClientTxnReq;
import io.axor.raft.proto.PeerProto.ClientTxnRes;
import io.axor.raft.proto.PeerProto.CommitResult;
import io.axor.raft.proto.PeerProto.LogAppend;
import io.axor.raft.proto.PeerProto.LogAppendAck;
import io.axor.raft.proto.PeerProto.LogId;
import io.axor.raft.proto.PeerProto.PeerMessage;
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
        commitedId = append.getEntriesList().getLast().getId();
        clientTxnList = txnContext.clientTxnList();
        term = append.getTerm();
        // local append entries
        logAppend(append).observe((res, e) -> {
            AppendResult.Status status;
            if (e != null) {
                LOG.error("LogAppend[txnId={}, commitedId={}, peer={}] error",
                        txnId, commitedId, raftContext().getSelfPeer().peer(), e);
                status = AppendResult.Status.SYSTEM_ERROR;
            } else {
                status = res.getStatus();
            }
            if (!isAppendSuccess(status)) {
                LOG.error("LogAppend[txnId={}, commitedId={}, peer={}] failure with status {}",
                        txnId, commitedId, raftContext().getSelfPeer().peer(), status);
                context().system()
                        .systemFailure(new RuntimeException("Leader local log append failure"));
            }
            LogAppendAck ack = LogAppendAck.newBuilder()
                    .setTxnId(txnId)
                    .setTerm(term)
                    .setResult(AppendResult.newBuilder()
                            .setStatus(status)
                            .setCommited(raftState().getCommitedId())
                            .addAllUncommited(raftState().getUncommitedId()))
                    .build();
            ActorUnsafe.tellInline(self(), peerMsg(ack), self());
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
            LogId appendFirstId = append.getEntries(0).getId();
            if (followerLogEndId == null || followerLogEndId.getIndex() == appendFirstId.getIndex() - 1) {
                peer.peerRef().tell(peerMsg(append), self());
            } else {
                logReadForSync(followerState.getCommited(), followerState.getUncommited())
                        .observe((l, e) -> {
                            if (e != null) {
                                LOG.error("Read for sync error, Follower {} state suspicious",
                                        peer.peer(), e);
                                return;
                            }
                            var msg = LogAppend.newBuilder()
                                    .setTxnId(txnId)
                                    .setTerm(term)
                                    .addAllEntries(l)
                                    .setLeaderCommited(raftState().getCommitedId())
                                    .build();
                            peer.peerRef().tell(peerMsg(msg), self());
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
    protected Behavior<PeerMessage> onClientTxnReq(ClientTxnReq msg) {
        leaderContext().bufferClientCtx(msg);
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onLogAppendAck(LogAppendAck msg) {
        if (msg.getTxnId() != txnId) {
            return Behaviors.unhandled();
        }
        RaftContext raftContext = raftContext();
        PeerInstance peerOfSender = raftContext.getPeerOfSender();
        Peer peer = peerOfSender.peer();
        FollowerState followerState = leaderContext().getFollowerStates().get(peer);
        assert followerState.getLatestTxnId() == txnId;
        AppendResult result = msg.getResult();
        followerState.setCommited(result.getCommited());
        followerState.setUncommited(result.getUncommitedList());

        if (!peerOfSender.isSelf()) {
            // follower存在延迟，还要继续追加写数据
            if (needContinueAppend(result)) {
                logReadForSync(followerState.getCommited(), followerState.getUncommited())
                        .observe((l, e) -> {
                            if (e != null) {
                                LOG.error("Read for sync error, {} state suspicious", peer, e);
                                return;
                            }
                            var m = PeerProto.LogAppend.newBuilder()
                                    .setTxnId(msg.getTxnId())
                                    .setTerm(term)
                                    .addAllEntries(l)
                                    .setLeaderCommited(commitedId)
                                    .build();
                            peerOfSender.peerRef().tell(peerMsg(m), self());
                        });
                return Behaviors.same();
            }
        }
        // 已经收到ack
        if (!ackedPeer.add(peer)) {
            LOG.warn("Already receive ack from {}, ignore", peer);
            return Behaviors.same();
        }

        if (isAppendSuccess(result.getStatus())) {
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
                        signal = new CommitSignal(txnId, CommitResult.Status.SYSTEM_ERROR);
                    } else {
                        signal = new CommitSignal(txnId, s.getStatus());
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
                return finishTxnAndReturn(ClientTxnRes.Status.APPEND_FAILURE, ByteString.empty());
            }
        }
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onSignal(Signal signal) {
        if (signal instanceof CommitSignal(var id, var status) && id == txnId) {
            if (isCommitSuccess(status)) {
                long term = raftState().getCurrentTerm();
                LogId commitedId = raftState().getCommitedId();
                PeerProto.LeaderHeartbeat heartbeat = PeerProto.LeaderHeartbeat.newBuilder()
                        .setTerm(term)
                        .setLeaderCommited(commitedId)
                        .build();
                for (PeerInstance peer : raftContext().getPeers()) {
                    if (peer.isSelf()) {
                        continue;
                    }
                    peer.peerRef().tell(peerMsg(heartbeat), self());
                }
                return finishTxnAndReturn(ClientTxnRes.Status.SUCCESS, ByteString.empty());
            } else {
                Behavior<PeerMessage> behavior =
                        finishTxnAndReturn(ClientTxnRes.Status.COMMIT_FAILURE,
                        ByteString.copyFromUtf8(status.name()));
                LOG.error("LogCommit[txnId={}, commitedId={}, peer={}] failure with status {}",
                        txnId, commitedId, raftContext().getSelfPeer().peer(), status);
                context().system()
                        .systemFailure(new RuntimeException("Leader local log commit failure"));
                return behavior;
            }
        }
        if (signal instanceof TimeoutSignal(var id) && id == txnId) {
            return finishTxnAndReturn(ClientTxnRes.Status.APPEND_TIMEOUT, ByteString.empty());
        }
        return Behaviors.unhandled();
    }

    @Override
    protected void onBehaviorChanged() {
        finishTxn(ClientTxnRes.Status.CANCELED, ByteString.empty());
    }

    private void finishTxn(ClientTxnRes.Status resStatus, ByteString msg) {
        if (txnFinished) {
            return;
        }
        timeoutFuture.cancel(false);
        for (RaftContext.ClientTxn clientTxn : clientTxnList) {
            var reply = ClientTxnRes.newBuilder()
                    .setTxnId(clientTxn.txnId())
                    .setStatus(resStatus)
                    .setData(msg)
                    .build();
            clientTxn.clientRef().tell(clientMsg(reply), self());
        }
        txnFinished = true;
    }

    private Behavior<PeerMessage> finishTxnAndReturn(ClientTxnRes.Status resStatus,
                                                     ByteString msg) {
        finishTxn(resStatus, msg);
        if (leaderContext().bufferIsEmpty()) {
            return new LeaderIdleBehavior(raftContext());
        } else {
            return new LeaderInTxnBehavior(raftContext());
        }
    }

    private record TimeoutSignal(long txnId) implements Signal {
    }

    private record CommitSignal(long txnId, CommitResult.Status commitStatus) implements Signal {
    }
}
