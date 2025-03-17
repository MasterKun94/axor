package io.masterkun.axor.cluster;

import com.typesafe.config.Config;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.EventStream;
import io.masterkun.axor.api.Pubsub;
import io.masterkun.axor.cluster.config.MembershipConfig;
import io.masterkun.axor.cluster.membership.DefaultSplitBrainResolver;
import io.masterkun.axor.cluster.membership.Member;
import io.masterkun.axor.cluster.membership.MembershipActor;
import io.masterkun.axor.cluster.membership.MembershipCommand;
import io.masterkun.axor.cluster.membership.MembershipListener;
import io.masterkun.axor.cluster.membership.MembershipMessage;
import io.masterkun.axor.cluster.membership.MetaKey;
import io.masterkun.axor.commons.config.ConfigMapper;
import io.masterkun.axor.runtime.MsgType;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Cluster {

    private final String name;
    private final ActorSystem system;
    private final ActorRef<MembershipMessage> actor;
    private final Pubsub<ClusterEvent> clusterEventPubsub;
    private final Map<String, Pubsub<?>> pubsubs = new ConcurrentHashMap<>();
    private final MembershipConfig config;
    private final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
    private volatile LocalMemberState localMemberState = LocalMemberState.NONE;

    public Cluster(String name, Config config, ActorSystem system) {
        this.name = name;
        this.system = system;
        this.config = ConfigMapper.map(config, MembershipConfig.class);
        var splitBrainResolver = new DefaultSplitBrainResolver(2, 2);
        this.actor = system.start(ctx ->
                new MembershipActor(ctx, this.config, splitBrainResolver), "membership"
        );
        this.clusterEventPubsub = Pubsub.create(system, MsgType.of(ClusterEvent.class), false);
        addListener(new MembershipListener() {
            @Override
            public void onLocalStateChange(LocalMemberState currentState) {
                localMemberState = currentState;
                var event = new ClusterEvent.LocalStateChange(currentState);
                clusterEventPubsub.publishToAll(event, ActorRef.noSender());
                if (currentState == LocalMemberState.LEFT) {
                    shutdownFuture.complete(null);
                }
            }

            @Override
            public void onMemberStateChange(Member member, MemberState from, MemberState to) {
                var clusterMember = ClusterMember.of(member);
                var event = new ClusterEvent.MemberStateChanged(clusterMember, to, from);
                clusterEventPubsub.publishToAll(event, ActorRef.noSender());
            }

            @Override
            public void onMemberUpdate(Member from, Member to) {
                var clusterMember = ClusterMember.of(to);
                var event = new ClusterEvent.MemberMetaInfoChanged(clusterMember, from.metaInfo());
                clusterEventPubsub.publishToAll(event, ActorRef.noSender());
            }
        });
        if (this.config.join().autoJoin()) {
            manager().tell(MembershipCommand.JOIN);
        }
    }

    public String name() {
        return name;
    }

    public ActorSystem system() {
        return system;
    }

    public ActorRef<MembershipCommand> manager() {
        return actor.cast(MsgType.of(MembershipCommand.class));
    }

    public EventStream<ClusterEvent> clusterEvents() {
        return clusterEventPubsub;
    }

    @SuppressWarnings("unchecked")
    public <T> Pubsub<T> pubsub(String topic, MsgType<T> msgType) {
        return (Pubsub<T>) pubsubs.compute(topic, (k, v) -> {
            if (v == null) {
                return new ClusterPubsub<>(topic, msgType, this);
            }
            if (v.msgType().equals(msgType)) {
                return v;
            }
            throw new IllegalArgumentException("MsgType mismatch, expected: " + v.msgType() +
                    ", actual: " + msgType);
        });
    }

    public LocalMemberState localMemberState() {
        return localMemberState;
    }

    public MembershipConfig config() {
        return config;
    }

    void addListener(MembershipListener listener) {
        actor.tell(MembershipMessage.addListener(listener, false));
    }

    void updateMetaInfo(MetaKey.Action... actions) {
        actor.tell(MembershipMessage.updateMetaInfo(actions));
    }
}
