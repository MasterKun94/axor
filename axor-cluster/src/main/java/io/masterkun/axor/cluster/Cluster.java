package io.masterkun.axor.cluster;

import com.typesafe.config.Config;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.EventStream;
import io.masterkun.axor.api.Pubsub;
import io.masterkun.axor.api.SystemCacheKey;
import io.masterkun.axor.cluster.config.MembershipConfig;
import io.masterkun.axor.cluster.membership.DefaultSplitBrainResolver;
import io.masterkun.axor.cluster.membership.Member;
import io.masterkun.axor.cluster.membership.MembershipActor;
import io.masterkun.axor.cluster.membership.MembershipCommand;
import io.masterkun.axor.cluster.membership.MembershipListener;
import io.masterkun.axor.cluster.membership.MembershipMessage;
import io.masterkun.axor.cluster.membership.MetaKey;
import io.masterkun.axor.commons.config.ConfigMapper;
import io.masterkun.axor.commons.task.DependencyTask;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.stateeasy.concurrent.EventPromise;
import io.masterkun.stateeasy.concurrent.EventStage;
import org.jetbrains.annotations.ApiStatus.Internal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Cluster {
    private static final Map<SystemCacheKey, Cluster> cache = new ConcurrentHashMap<>();
    public static final String DEFAULT_NAME = "default-cluster";
    private final String name;
    private final ActorSystem system;
    private final ActorRef<MembershipMessage> actor;
    private final Pubsub<ClusterEvent> clusterEventPubsub;
    private final Map<String, Pubsub<?>> pubsubs = new ConcurrentHashMap<>();
    private final MembershipConfig config;
    private final EventPromise<Void> joinPromise;
    private final EventPromise<Void> leavePromise;
    private volatile LocalMemberState localMemberState = LocalMemberState.NONE;

    Cluster(String name, Config config, ActorSystem system) {
        this.name = name;
        this.system = system;
        this.config = ConfigMapper.map(config, MembershipConfig.class);
        if (this.config.join().seeds().isEmpty()) {
            throw new IllegalArgumentException("No join seed");
        }
        var splitBrainResolver = new DefaultSplitBrainResolver(2, 2);
        EventDispatcher dispatcher = system.getDispatcherGroup().nextDispatcher();
        this.actor = system.start(ctx ->
                        new MembershipActor(ctx, this.config, splitBrainResolver),
                "membership", dispatcher);
        this.clusterEventPubsub = Pubsub.get("__ClusterEvent_" + name,
                MsgType.of(ClusterEvent.class), false, system);
        this.joinPromise = dispatcher.newPromise();
        this.leavePromise = dispatcher.newPromise();
        this.system.shutdownHooks()
                .register(new DependencyTask("cluster-" + name, "root") {
                    @Override
                    public CompletableFuture<Void> run() {
                        return leave().toCompletableFuture();
                    }
                });
        addListener(new ClusterMembershipListener());
        if (this.config.join().autoJoin()) {
            join();
        }
    }

    public static Cluster get(ActorSystem system) {
        return get(DEFAULT_NAME, system);
    }

    public static Cluster get(String name, ActorSystem system) {
        return cache.compute(new SystemCacheKey(name, system), (k, v) -> {
            String key = "axor.cluster." + name;
            if (!system.config().hasPath(key)) {
                throw new IllegalArgumentException("No config for cluster " + name);
            }
            Config config = system.config().getConfig(key);
            return new Cluster(name, config, system);
        });
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

    public EventStage<Void> join() {
        manager().tell(MembershipCommand.JOIN);
        return joinPromise;
    }

    public EventStage<Void> leave() {
        manager().tell(MembershipCommand.LEAVE);
        return leavePromise;
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

    @Internal
    void addListener(MembershipListener listener) {
        actor.tell(MembershipMessage.addListener(listener, false));
    }

    @Internal
    void updateMetaInfo(MetaKey.Action... actions) {
        actor.tell(MembershipMessage.updateMetaInfo(actions));
    }

    private class ClusterMembershipListener implements MembershipListener {
        @Override
        public void onLocalStateChange(LocalMemberState current) {
            if (!joinPromise.isDone()) {
                if (current == LocalMemberState.UP || current == LocalMemberState.WEAKLY_UP) {
                    joinPromise.success(null);
                } else if (current == LocalMemberState.LEFT) {
                    joinPromise.failure(new RuntimeException("member left"));
                }
            }
            localMemberState = current;
            var event = new ClusterEvent.LocalStateChange(current);
            clusterEventPubsub.publishToAll(event, ActorRef.noSender());
        }

        @Override
        public void onLocalMemberStopped() {
            leavePromise.success(null);
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
    }
}
