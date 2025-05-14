package io.axor.raft.behaviors;

import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.PeerInstance;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftContext.FollowerState;
import io.axor.raft.TxnManager.Key;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.ClientTxnReq;
import io.axor.raft.proto.PeerProto.LogAppendAck;
import io.axor.raft.proto.PeerProto.LogId;
import io.axor.raft.proto.PeerProto.PeerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LeaderIdleBehavior extends AbstractLeaderBehavior {
    private static final Logger LOG = LoggerFactory.getLogger(LeaderIdleBehavior.class);

    protected LeaderIdleBehavior(RaftContext raftContext) {
        super(raftContext);
    }

    @Override
    protected Behavior<PeerMessage> onClientTxnReq(ClientTxnReq msg) {
        if (leaderContext().bufferClientCtx(msg)) {
            return new LeaderInTxnBehavior(raftContext());
        } else {
            // TODO
            assert raftContext().getTxnManager()
                    .isCommited(new Key(msg.getClientId(), msg.getSeqId()));
            return Behaviors.same();
        }
    }

    @Override
    protected Behavior<PeerMessage> onLogAppendAck(LogAppendAck msg) {
        PeerInstance peerOfSender = raftContext().getPeerOfSender();
        FollowerState followerState = leaderContext().getFollowerStates().get(peerOfSender.peer());
        if (followerState.getLatestTxnId() != msg.getTxnId()) {
            // ignore
            return Behaviors.same();
        }
        AppendResult result = msg.getResult();
        followerState.setCommited(result.getCommited());
        followerState.setUncommited(result.getUncommitedList());
        LogId commitedId = raftLogging().commitedId();
        long term = raftState().getCurrentTerm();
        if (!peerOfSender.isSelf()) {
            // follower存在延迟，还要继续追加写数据
            if (needContinueAppend(result)) {
                try {
                    List<PeerProto.LogEntry> res = logReadForSync(followerState.getCommited(),
                            followerState.getUncommited());
                    if (res.isEmpty()) {
                        LOG.error("ReadForSync query result should not be empty! bug?");
                    } else {
                        var m = PeerProto.LogAppend.newBuilder()
                                .setTxnId(msg.getTxnId())
                                .setTerm(term)
                                .addAllEntries(res)
                                .setLeaderCommited(commitedId)
                                .build();
                        peerOfSender.peerRef().tell(peerMsg(m), self());
                    }
                } catch (Exception e) {
                    LOG.error("Read for sync error, {} state suspicious",
                            peerOfSender.peer(), e);
                }
            }
        }
        return Behaviors.same();
    }
}
