package io.axor.raft.mediator;

import io.axor.raft.proto.PeerProto.MediatorMessage;

public interface ReqCoordinator {
    void onFailureRes(MediatorMessage.FailureRes msg);
    void onSuccessRes(MediatorMessage msg);
    void onReady();
    void onNoLeader();
    void onRedirect();
}
