package io.axor.raft.behaviors;

import com.google.protobuf.ByteString;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.Peer;
import io.axor.raft.PeerInstance;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftContext.FollowerState;
import io.axor.raft.RaftException;
import io.axor.raft.TxnManager;
import io.axor.raft.logging.RaftLogging;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static io.axor.api.MessageUtils.loggable;

public class LeaderInTxnBehavior extends AbstractLeaderBehavior {
    private static final Logger LOG = LoggerFactory.getLogger(LeaderInTxnBehavior.class);

    private final long txnId;
    private final long term;
    private final List<TxnManager.Key> txnKeyList;
    private final ScheduledFuture<?> timeoutFuture;
    private final int majorityCount;
    private final LogId nextCommitedId;
    private final Set<Peer> ackedPeer;
    private int successCnt = 1;
    private int failureCnt = 0;
    private boolean txnFinished = false;

    protected LeaderInTxnBehavior(RaftContext raftContext) {
        super(raftContext);
        ackedPeer = new HashSet<>(raftContext.getPeers().size());
        majorityCount = raftContext.getPeers().size() / 2 + 1;
        txnId = raftContext().generateTxnId();
        LogAppend append = leaderContext().prepareForTxn(txnId);
        nextCommitedId = append.getEntriesList().getLast().getId();
        term = append.getTerm();
        LOG.info("Start transaction with txnId={}", txnId);
        // local append entries
        RaftLogging raftLogging = raftLogging();
        AppendResult res;
        try {
            res = raftLogging.append(append.getPrevLogId(), append.getEntriesList());
            if (isAppendSuccess(res.getStatus())) {
                LOG.debug("Local LogAppend[txnId={}, commitedId={}, peer={}] success",
                        txnId, nextCommitedId, raftContext().getSelfPeer().peer());
            } else {
                LOG.error("Local LogAppend[txnId={}, commitedId={}, peer={}] failure status {}",
                        txnId, nextCommitedId, raftContext().getSelfPeer().peer(), res.getStatus());
                throw new RuntimeException("local log append failure status: " + res.getStatus());
            }
        } catch (RaftException e) {
            LOG.error("Local LogAppend[txnId={}, commitedId={}, peer={}] error",
                    txnId, nextCommitedId, raftContext().getSelfPeer().peer(), e);
            throw new RuntimeException(e);
        }
        txnKeyList = new ArrayList<>(append.getEntriesCount());
        for (PeerProto.LogEntry logEntry : append.getEntriesList()) {
            PeerProto.LogValue logValue = logEntry.getValue();
            TxnManager.Key key = new TxnManager.Key(logValue.getClientId(), logValue.getSeqId());
            txnKeyList.add(key);
        }
        // append entries to peer
        Map<Peer, FollowerState> followerStates = leaderContext().getFollowerStates();
        LogId firstAppendId = append.getEntries(0).getId();
        for (PeerInstance peer : raftContext().getPeers()) {
            FollowerState followerState = followerStates.get(peer.peer());
            followerState.setLatestTxnId(txnId);
            if (peer.isSelf()) {
                continue;
            }
            LogId followerLogEndId = followerState.getLogEndId();
            if (followerLogEndId == null || followerLogEndId.getIndex() == firstAppendId.getIndex() - 1) {
                peer.peerRef().tell(peerMsg(append), self());
            } else {
                try {
                    var read = logReadForSync(followerState.getCommited(),
                            followerState.getUncommited());
                    var msg = LogAppend.newBuilder()
                            .setTxnId(txnId)
                            .setTerm(term)
                            .addAllEntries(read)
                            .setLeaderCommited(raftLogging.commitedId())
                            .build();
                    peer.peerRef().tell(peerMsg(msg), self());
                } catch (Exception e) {
                    LOG.error("Read for sync error, Follower {} state suspicious", peer.peer(), e);
                }
            }
        }
        // schedule timeout
        Duration timeout = config().logAppendTimeout();
        timeoutFuture = context().scheduler().scheduleSignal(
                new TimeoutSignal(txnId), self(),
                timeout.toMillis(), TimeUnit.MILLISECONDS);
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
        Peer senderPeer = peerOfSender.peer();
        LOG.info("Receive ack from {}", senderPeer);
        FollowerState followerState = leaderContext().getFollowerStates().get(senderPeer);
        assert followerState.getLatestTxnId() == txnId;
        AppendResult result = msg.getResult();
        followerState.setCommited(result.getCommited());
        followerState.setUncommited(result.getUncommitedList());

        if (!peerOfSender.isSelf()) {
            // follower存在延迟，还要继续追加写数据
            if (needContinueAppend(result)) {
                try {
                    var read = logReadForSync(followerState.getCommited(),
                            followerState.getUncommited());
                    var m = PeerProto.LogAppend.newBuilder()
                            .setTxnId(msg.getTxnId())
                            .setTerm(term)
                            .addAllEntries(read)
                            .setLeaderCommited(raftLogging().commitedId())
                            .build();
                    peerOfSender.peerRef().tell(peerMsg(m), self());
                } catch (Exception e) {
                    LOG.error("Read for sync error, Follower {} state suspicious", senderPeer, e);
                }
                return Behaviors.same();
            }
        }
        // 已经收到ack
        if (!ackedPeer.add(senderPeer)) {
            LOG.warn("Already receive ack from {}, ignore", senderPeer);
            return Behaviors.same();
        }

        if (isAppendSuccess(result.getStatus())) {
            successCnt++;
            // 成功数量达到多数，执行本地commit
            if (successCnt >= majorityCount) {
                timeoutFuture.cancel(false);
                CommitResult.Status status;
                try {
                    status = raftLogging().commit(nextCommitedId).getStatus();
                } catch (RaftException e) {
                    LOG.error("LogCommit[txnId={}, commitedId={}, senderPeer={}] error",
                            txnId, nextCommitedId, raftContext.getSelfPeer().peer(), e);
                    status = CommitResult.Status.SYSTEM_ERROR;
                }
                return commitAndFinishTxn(status);
            }
        } else {
            failureCnt++;
            // 失败数量达到多数
            if (failureCnt >= majorityCount) {
                return finishTxnAndReturn(ClientTxnRes.Status.APPEND_FAILURE, ByteString.empty());
            }
        }
        return Behaviors.same();
    }

    @Override
    protected Behavior<PeerMessage> onSignal(Signal signal) {
        if (signal instanceof TimeoutSignal(var id) && id == txnId) {
            LOG.warn("Transaction timeout");
            return finishTxnAndReturn(ClientTxnRes.Status.APPEND_TIMEOUT, ByteString.empty());
        }
        return Behaviors.unhandled();
    }

    @Override
    protected void onBehaviorChanged() {
        finishTxn(ClientTxnRes.Status.CANCELED, ByteString.empty());
    }

    private Behavior<PeerMessage> commitAndFinishTxn(CommitResult.Status status) {
        if (isCommitSuccess(status)) {
            long term = raftState().getCurrentTerm();
            LogId commitedId = raftLogging().commitedId();
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
            LOG.info("LogCommit[txnId={}, commitedId={}, peer={}] success", txnId,
                    loggable(nextCommitedId), raftContext().getSelfPeer().peer());
            return finishTxnAndReturn(ClientTxnRes.Status.SUCCESS, ByteString.empty());
        } else {
            Behavior<PeerMessage> behavior =
                    finishTxnAndReturn(ClientTxnRes.Status.COMMIT_FAILURE,
                            ByteString.copyFromUtf8(status.name()));
            LOG.error("LogCommit[txnId={}, commitedId={}, peer={}] failure status {}", txnId,
                    loggable(nextCommitedId), raftContext().getSelfPeer().peer(), status);
            context().system()
                    .systemFailure(new RuntimeException("Leader local log commit failure"));
            return behavior;
        }
    }

    private void finishTxn(ClientTxnRes.Status resStatus, ByteString msg) {
        if (txnFinished) {
            return;
        }
        timeoutFuture.cancel(false);
        TxnManager txnManager = raftContext().getTxnManager();
        if (resStatus != ClientTxnRes.Status.SUCCESS) {
            for (TxnManager.Key key : txnKeyList) {
                txnManager.finishTxn(key, resStatus, msg);
            }
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
}
