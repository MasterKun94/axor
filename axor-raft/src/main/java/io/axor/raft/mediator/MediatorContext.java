package io.axor.raft.mediator;

import io.axor.api.ActorRef;
import io.axor.commons.collection.LongObjectHashMap;
import io.axor.commons.collection.LongObjectMap;
import io.axor.raft.proto.PeerProto;

import java.util.List;

public class MediatorContext {
    private final LongObjectMap<ClientReqState> clientReqCtxMap = new LongObjectHashMap<>();
    private long term;
    private ActorRef<PeerProto.PeerMessage> leader;
    private List<ActorRef<PeerProto.PeerMessage>> peers;

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public ActorRef<PeerProto.PeerMessage> getLeader() {
        return leader;
    }

    public void setLeader(ActorRef<PeerProto.PeerMessage> leader) {
        this.leader = leader;
    }

    public List<ActorRef<PeerProto.PeerMessage>> getPeers() {
        return peers;
    }

    public void setPeers(List<ActorRef<PeerProto.PeerMessage>> peers) {
        this.peers = peers;
    }

    public LongObjectMap<ClientReqState> getClientReqCtxMap() {
        return clientReqCtxMap;
    }

    public static final class ClientReqState {
        private final PeerProto.MediatorMessage msg;
        private final ActorRef<PeerProto.PeerMessage> client;
        private final long clientSeqId;
        private final long mediatorSeqId;
        private int retryNum;
        private long requestTime;

        public ClientReqState(PeerProto.MediatorMessage msg, ActorRef<PeerProto.PeerMessage> client, long clientSeqId, long mediatorSeqId) {
            this.msg = msg;
            this.client = client;
            this.clientSeqId = clientSeqId;
            this.mediatorSeqId = mediatorSeqId;
        }

        public PeerProto.MediatorMessage getMsg() {
            return msg;
        }

        public ActorRef<PeerProto.PeerMessage> getClient() {
            return client;
        }

        public long getClientSeqId() {
            return clientSeqId;
        }

        public long getMediatorSeqId() {
            return mediatorSeqId;
        }

        public int getRetryNum() {
            return retryNum;
        }

        public void setRetryNum(int retryNum) {
            this.retryNum = retryNum;
        }

        public long getRequestTime() {
            return requestTime;
        }

        public void setRequestTime(long requestTime) {
            this.requestTime = requestTime;
        }
    }
}
