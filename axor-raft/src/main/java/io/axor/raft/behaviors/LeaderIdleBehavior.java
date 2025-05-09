package io.axor.raft.behaviors;

import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.raft.PeerInstance;
import io.axor.raft.RaftContext;
import io.axor.raft.RaftContext.FollowerState;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.ClientTxnReq;
import io.axor.raft.proto.PeerProto.LogAppendAck;
import io.axor.raft.proto.PeerProto.LogId;
import io.axor.raft.proto.PeerProto.PeerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaderIdleBehavior extends AbstractLeaderBehavior {
    private static final Logger LOG = LoggerFactory.getLogger(LeaderIdleBehavior.class);

    protected LeaderIdleBehavior(RaftContext raftContext) {
        super(raftContext);
    }

    @Override
    protected Behavior<PeerMessage> onClientTxnReq(ClientTxnReq msg) {
        leaderContext().bufferClientCtx(msg);
        return new LeaderInTxnBehavior(raftContext());
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
        LogId commitedId = raftState().getCommitedId();
        long term = raftState().getCurrentTerm();
        if (!peerOfSender.isSelf()) {
            // follower存在延迟，还要继续追加写数据
            if (needContinueAppend(result)) {
                logReadForSync(followerState.getCommited(), followerState.getUncommited())
                        .observe((l, e) -> {
                            if (e != null) {
                                LOG.error("Read for sync error, {} state suspicious",
                                        peerOfSender.peer(), e);
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
        return Behaviors.same();
    }
}
