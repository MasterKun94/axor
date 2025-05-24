package io.axor.raft.mediator;

import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.raft.proto.PeerProto.MediatorMessage;

public class DefaultReqCoordinator implements ReqCoordinator {
    private final ActorContext<MediatorMessage> context;
    private final MediatorContext mediatorContext;

    private final MediatorMessage req;
    private final ActorRef<MediatorMessage> client;
    private final long createTime;
    private long requestTime;
    private boolean ready;

    public DefaultReqCoordinator(ActorContext<MediatorMessage> context,
                                 MediatorContext mediatorContext,
                                 MediatorMessage req,
                                 ActorRef<MediatorMessage> client) {
        this.context = context;
        this.mediatorContext = mediatorContext;
        this.req = req;
        this.client = client;
        this.createTime = System.currentTimeMillis();
    }

    @Override
    public void onFailureRes(MediatorMessage.FailureRes msg) {

    }

    @Override
    public void onSuccessRes(MediatorMessage msg) {
        client.tell(msg, context.self());
    }

    @Override
    public void onReady() {
        assert !ready;
        ready = true;

    }

    @Override
    public void onNoLeader() {
        assert ready;
        ready = false;
    }

    @Override
    public void onRedirect() {

    }
}
