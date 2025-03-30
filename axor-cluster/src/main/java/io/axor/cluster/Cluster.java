package io.axor.cluster;

import com.typesafe.config.Config;
import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.api.Pubsub;
import io.axor.api.SystemCacheKey;
import io.axor.cluster.config.MembershipConfig;
import io.axor.cluster.membership.DefaultSplitBrainResolver;
import io.axor.cluster.membership.MemberIdGenerator;
import io.axor.cluster.membership.MembershipActor;
import io.axor.cluster.membership.MembershipCommand;
import io.axor.cluster.membership.MembershipMessage;
import io.axor.cluster.membership.MetaKey;
import io.axor.commons.config.ConfigMapper;
import io.axor.commons.task.DependencyTask;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.masterkun.stateeasy.concurrent.EventPromise;
import io.masterkun.stateeasy.concurrent.EventStage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Cluster {
    public static final String DEFAULT_NAME = "default-cluster";
    private static final Map<SystemCacheKey, Cluster> cache = new ConcurrentHashMap<>();
    private final long uid;
    private final String name;
    private final ActorSystem system;
    private final ActorRef<MembershipMessage> actor;
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
        String memberActorName = "cluster/" + name + "/membership";
        this.uid = MemberIdGenerator.create(system.address(memberActorName)).nextId();
        this.actor = system.start(ctx -> new MembershipActor(uid,
                ctx, this.config, splitBrainResolver), memberActorName, dispatcher);
        this.joinPromise = dispatcher.newPromise();
        this.leavePromise = dispatcher.newPromise();
        this.system.shutdownHooks().register(new DependencyTask("cluster-" + name, "root") {
            @Override
            public CompletableFuture<Void> run() {
                return leave().toCompletableFuture();
            }
        });
        system.start(ClusterMembershipListener::new, "cluster/" + name + "/listener");
        updateMetaInfo(BuiltinMetaKeys.SELF_DATACENTER.upsert(this.config.datacenter()),
                BuiltinMetaKeys.SELF_ROLES.upsert(this.config.roles()));
        if (this.config.join().autoJoin()) {
            join();
        }
    }

    public static Cluster get(ActorSystem system) {
        return get(DEFAULT_NAME, system);
    }

    public static Cluster get(String name, ActorSystem system) {
        return cache.computeIfAbsent(new SystemCacheKey(name, system), k -> {
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

    public long uid() {
        return uid;
    }

    public ActorSystem system() {
        return system;
    }

    public ActorRef<MembershipCommand> manager() {
        return actor.cast(MsgType.of(MembershipCommand.class));
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
            throw new IllegalArgumentException("MsgType mismatch, expected: " + v.msgType() + ", " +
                                               "actual: " + msgType);
        });
    }

    public LocalMemberState localMemberState() {
        return localMemberState;
    }

    public MembershipConfig config() {
        return config;
    }

    public void addListener(ActorRef<ClusterEvent> listener) {
        actor.tell(MembershipMessage.addListener(listener, false));
    }

    public void removeListener(ActorRef<ClusterEvent> listener) {
        actor.tell(MembershipMessage.removeListener(listener));
    }

    public void updateMetaInfo(MetaKey.Action... actions) {
        actor.tell(MembershipMessage.updateMetaInfo(actions));
    }

    private class ClusterMembershipListener extends Actor<ClusterEvent> {

        protected ClusterMembershipListener(ActorContext<ClusterEvent> context) {
            super(context);
        }

        @Override
        public void onStart() {
            Cluster.this.addListener(self());
            super.onStart();
        }

        @Override
        public void onReceive(ClusterEvent clusterEvent) {
            if (clusterEvent instanceof ClusterEvent.LocalStateChange(var current)) {
                if (!joinPromise.isDone()) {
                    if (current == LocalMemberState.UP || current == LocalMemberState.WEAKLY_UP) {
                        joinPromise.success(null);
                    } else if (current == LocalMemberState.LEFT) {
                        joinPromise.failure(new RuntimeException("member left"));
                    }
                }
                localMemberState = current;
            } else if (clusterEvent instanceof ClusterEvent.LocalMemberStopped) {
                leavePromise.success(null);
                context().stop();
            }
        }

        @Override
        public void preStop() {
            if (!leavePromise.isDone()) {
                Cluster.this.removeListener(self());
            }
            super.preStop();
        }

        @Override
        public MsgType<ClusterEvent> msgType() {
            return MsgType.of(ClusterEvent.class);
        }
    }
}
