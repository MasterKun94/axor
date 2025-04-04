package io.axor.cluster.singleton;

import io.axor.api.AbstractActor;
import io.axor.api.Actor;
import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorCreator;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.SystemEvent;
import io.axor.api.impl.ActorUnsafe;
import io.axor.cluster.BuiltinMetaKeys;
import io.axor.cluster.Cluster;
import io.axor.cluster.ClusterEvent;
import io.axor.cluster.ClusterMember;
import io.axor.cluster.LocalMemberState;
import io.axor.cluster.membership.MetaInfo;
import io.axor.cluster.proto.MembershipProto.SingletonManagerMessage;
import io.axor.commons.collection.LongObjectHashMap;
import io.axor.commons.collection.LongObjectMap;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.ActorRuntimeException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import io.axor.runtime.serde.protobuf.ProtobufUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static io.axor.api.Behaviors.log;
import static io.axor.api.Behaviors.receive;
import static io.axor.api.Behaviors.same;
import static io.axor.api.Behaviors.unhandled;

class ClusterSingletonProxy<T> extends Actor<T> {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterSingletonProxy.class);
    private static final Level LOG_LEVEL = Level.INFO;
    private static final MsgType<SingletonManagerMessage> MANAGER_MSG_TYPE =
            MsgType.of(SingletonManagerMessage.class);

    private final String name;
    private final SingletonConfig config;
    private final Cluster cluster;
    private final Signal stopSignal;
    private final MsgType<T> msgType;
    private final List<MsgAndSender<T>> bufferedMsg = new ArrayList<>();
    private final ActorCreator<T> singletonCreator;
    private final LongObjectMap<ClusterMember> availableMembers = new LongObjectHashMap<>();
    private ScheduledFuture<?> stopFuture;
    private ActorRef<SingletonManagerMessage> manager;
    private ActorRef<ClusterEvent> listener;
    private ActorRef<T> realInstance;
    private boolean servable;
    @Nullable
    private ClusterMember leaderMember;

    ClusterSingletonProxy(String clusterName,
                          String name,
                          SingletonConfig config,
                          Signal stopSignal,
                          MsgType<T> msgType,
                          ActorContext<T> ctx, ActorCreator<T> singletonCreator) {
        super(ctx);
        this.name = name;
        this.config = config;
        this.stopSignal = stopSignal;
        this.msgType = msgType;
        this.singletonCreator = singletonCreator;
        this.cluster = clusterName == null ? Cluster.get(ctx.system()) :
                Cluster.get(clusterName, ctx.system());
    }

    private String managerName() {
        return "cluster/singleton/" + name + "/manager";
    }

    private String listenerName() {
        return "cluster/singleton/" + name + "/listener";
    }

    private String instanceName() {
        return "cluster/singleton/" + name + "/instance";
    }

    @Override
    public void onStart() {
        manager = context().startChild(SingletonManager::new, managerName());
        listener = context().startChild(SingletonListener::new, listenerName());
    }

    @Override
    public void onReceive(T t) {
        if (realInstance != null) {
            assert bufferedMsg.isEmpty();
            realInstance.tell(t, sender());
        } else {
            bufferedMsg.add(new MsgAndSender<>(t, sender()));
        }
    }

    @Override
    public void preStop() {
        if (stopFuture != null) {
            stopFuture.cancel(false);
            stopFuture = null;
        }
        cluster.addListener(listener);
        super.preStop();
    }

    @Override
    public MsgType<T> msgType() {
        return msgType;
    }

    private void flushBuffer() {
        if (bufferedMsg.isEmpty()) {
            return;
        }
        assert realInstance != null;
        for (MsgAndSender<T> elem : bufferedMsg) {
            realInstance.tell(elem.msg, elem.sender);
        }
    }

    private record MsgAndSender<T>(T msg, ActorRef<?> sender) {
    }

    private class SingletonListener extends Actor<ClusterEvent> {
        private final Logger LOG = LoggerFactory.getLogger(SingletonListener.class);

        protected SingletonListener(ActorContext<ClusterEvent> context) {
            super(context);
        }

        @Override
        public void onStart() {
            LOG.info("Listener is started");
            cluster.addListener(listener);
        }

        @Override
        public void preStop() {
            cluster.removeListener(listener);
        }

        @Override
        public void onReceive(ClusterEvent clusterEvent) {
            switch (clusterEvent) {
                case ClusterEvent.LocalStateChange(var state) -> {
                    if (servable) {
                        if (state != LocalMemberState.UP) {
                            servable = false;
                            ActorUnsafe.signalInline(manager, StateChange.UNSERVABLE);
                        }
                    } else {
                        if (state == LocalMemberState.UP) {
                            servable = true;
                            ActorUnsafe.signalInline(manager, StateChange.SERVABLE);
                        }
                    }
                }
                case ClusterEvent.MemberStateChanged(var member, var from, var to) -> {
                    LOG.info("MemberStateChanged(member={}, from={}, to={}", member, from, to);
                    if (!existsSingleton(member.metaInfo())) {
                        return;
                    }
                    if (from.ALIVE) {
                        if (!to.ALIVE) {
                            removeMember(member);
                        }
                    } else if (to.ALIVE) {
                        addMember(member);
                    }
                }
                case ClusterEvent.MemberMetaInfoChanged(var member, var prev) -> {
                    if (existsSingleton(member.metaInfo())) {
                        if (existsSingleton(prev)) {
                            assert leaderMember != null;
                            if (member.uid() == leaderMember.uid()) {
                                if (existsInstance(member.metaInfo())) {
                                    if (!existsInstance(prev)) {
                                        signalInstanceStarted(member);
                                    }
                                } else {
                                    if (existsInstance(prev)) {
                                        signalInstanceStopped(member);
                                    }
                                }
                            }
                        } else {
                            addMember(member);
                        }
                    } else if (existsSingleton(prev)) {
                        removeMember(member);
                    }
                }
                case ClusterEvent.LocalMemberStopped() ->
                        ClusterSingletonProxy.this.context().stop();
            }
        }

        private boolean existsSingleton(MetaInfo metaInfo) {
            var singletons = metaInfo.get(BuiltinMetaKeys.SINGLETONS);
            List<String> requireRoles = config.requireRoles();
            if (requireRoles != null && !requireRoles.isEmpty()) {
                List<String> roles = metaInfo.get(BuiltinMetaKeys.SELF_ROLES);
                for (String role : requireRoles) {
                    if (!roles.contains(role)) {
                        return false;
                    }
                }
            }
            return singletons.containsStates(name);
        }

        private boolean existsInstance(MetaInfo metaInfo) {
            return BuiltinMetaKeys.SINGLETONS.get(metaInfo)
                    .getStatesOrThrow(name);
        }

        private void signalInstanceStarted(ClusterMember member) {
            if (realInstance != null) {
                LOG.error("Receive instance started at [{}] but already exists instance [{}] " +
                          "internal", member, realInstance);
            }
            assert member == leaderMember;
            ActorUnsafe.signalInline(manager, StateChange.INSTANCE_STARTED);
        }

        private void signalInstanceStopped(ClusterMember member) {
            if (realInstance == null) {
                LOG.error("Receive instance stopped at [{}] but not exist internal", member);
            }
            assert member == leaderMember;
            ActorUnsafe.signalInline(manager, StateChange.INSTANCE_STOPPED);
        }

        private void addMember(ClusterMember member) {
            availableMembers.put(member.uid(), member);
            boolean b = leaderMember == null;
            if (b || member.uid() < leaderMember.uid()) {
                leaderMember = member;
                if (b) {
                    ActorUnsafe.signalInline(manager, StateChange.LEADER_ADDED);
                } else {
                    ActorUnsafe.signalInline(manager, StateChange.LEADER_CHANGED);
                }
                if (existsInstance(member.metaInfo())) {
                    signalInstanceStarted(member);
                }
            }
        }

        private void removeMember(ClusterMember member) {
            availableMembers.remove(member.uid());
            if (leaderMember != null && member.uid() == leaderMember.uid()) {
                leaderMember = availableMembers.values().stream()
                        .min(Comparator.comparingLong(ClusterMember::uid))
                        .orElse(null);
                if (existsInstance(member.metaInfo())) {
                    signalInstanceStopped(member);
                }
                if (leaderMember != null) {
                    ActorUnsafe.signalInline(manager, StateChange.LEADER_CHANGED);
                } else {
                    ActorUnsafe.signalInline(manager, StateChange.LEADER_REMOVED);
                }
            }
        }

        @Override
        public MsgType<ClusterEvent> msgType() {
            return MsgType.of(ClusterEvent.class);
        }
    }

    class SingletonManager extends AbstractActor<SingletonManagerMessage> {
        private ActorRef<T> instance;
        private ScheduledFuture<?> instanceStopFuture;

        protected SingletonManager(ActorContext<SingletonManagerMessage> context) {
            super(context);
        }

        @Override
        protected void preStart() {
            LOG.info("Manager is started");
            cluster.updateMetaInfo(BuiltinMetaKeys.SINGLETONS.update(s ->
                    s.toBuilder().putStates(name, false).build()));
        }

        @Override
        public void preStop() {
            cluster.updateMetaInfo(BuiltinMetaKeys.SINGLETONS.update(s ->
                    s.toBuilder().removeStates(name).build()));
        }

        @Override
        protected Behavior<SingletonManagerMessage> initialBehavior() {
            return unservableFollower();
        }

        private Behavior<SingletonManagerMessage> unservableFollower() {
            LOG.info("Manager state changed to UNSERVABLE_FOLLOWER");
            return log(receive(msg -> {
                assert !servable;
                if (msg.getType() == SingletonManagerMessage.Type.INSTANCE_READY) {
                    LOG.warn("Receive instance ready but self not servable, ignore msg {}",
                            ProtobufUtil.toString(msg));
                    return same();
                }
                return unhandled();
            }, signal -> {
                assert !servable;
                if (!(signal instanceof StateChange)) {
                    return unhandled();
                }
                switch ((StateChange) signal) {
                    case SERVABLE -> {
                        assert realInstance == null;
                        if (leaderMember != null && leaderMember.uid() == cluster.uid()) {
                            assert instance == null;
                            return instanceReady();
                        }
                        return servableFollower();
                    }
                    case INSTANCE_STARTED -> {
                        try {
                            assert leaderMember != null;
                            instance = context().system().get(ActorAddress.create(
                                            leaderMember.system(), leaderMember.address(),
                                            instanceName()),
                                    msgType);
                        } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                            throw new ActorRuntimeException(e);
                        }
                    }
                    case LEADER_CHANGED, LEADER_ADDED -> {
                        realInstance = instance = null;
                        assert leaderMember != null;
                    }
                    case LEADER_REMOVED, INSTANCE_STOPPED -> realInstance = instance = null;
                }
                return same();
            }), LOG, LOG_LEVEL);
        }

        private Behavior<SingletonManagerMessage> servableFollower() {
            LOG.info("Manager state changed to SERVABLE_FOLLOWER");
            assert servable;
            if (instance != null) {
                realInstance = instance;
                flushBuffer();
            }
            if (instance == null && leaderMember != null && leaderMember.uid() != cluster.uid()) {
                tryLeaderAck(getRemoteManager(leaderMember));
            }
            return log(receive(msg -> {
                assert servable;
                if (msg.getType() == SingletonManagerMessage.Type.INSTANCE_READY) {
                    if (leaderMember == null) {
                        LOG.warn("Receive instance ready but self detect no leader " +
                                 "member, ignore msg: {}", ProtobufUtil.toString(msg));
                    } else if (leaderMember.uid() != msg.getUid()) {
                        LOG.warn("Receive instance ready but self detect different leader " +
                                 "member, expect: {}, ignore msg: {}", leaderMember,
                                ProtobufUtil.toString(msg));
                    } else {
                        tryLeaderAck(sender(MANAGER_MSG_TYPE));
                    }
                    return same();
                }
                return unhandled();
            }, signal -> {
                if (!(signal instanceof StateChange)) {
                    return unhandled();
                }
                switch ((StateChange) signal) {
                    case UNSERVABLE -> {
                        realInstance = null;
                        return unservableFollower();
                    }
                    case INSTANCE_STARTED -> {
                        try {
                            assert leaderMember != null;
                            instance = context().system().get(ActorAddress.create(
                                            leaderMember.system(), leaderMember.address(),
                                            instanceName()),
                                    msgType);
                        } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                            throw new ActorRuntimeException(e);
                        }
                        realInstance = instance;
                        flushBuffer();
                    }
                    case INSTANCE_STOPPED -> realInstance = instance = null;
                    case LEADER_CHANGED, LEADER_ADDED -> {
                        realInstance = instance = null;
                        assert leaderMember != null;
                        if (leaderMember.uid() == cluster.uid()) {
                            return instanceReady();
                        }
                        tryLeaderAck(getRemoteManager(leaderMember));
                    }
                    case LEADER_REMOVED -> {
                        realInstance = instance = null;
                        if (leaderMember != null && leaderMember.uid() == cluster.uid()) {
                            return instanceReady();
                        }
                    }
                }
                return same();
            }), LOG, LOG_LEVEL);
        }

        private Behavior<SingletonManagerMessage> instanceReady() {
            LOG.info("Manager state changed to INSTANCE_READY");
            Duration interval = config.instanceReadyReqInterval();
            Duration delay = config.instanceReadyReqDelay();
            var sc = context().dispatcher().scheduleAtFixedRate(() -> {
                for (ClusterMember member : availableMembers.values()) {
                    if (member.uid() == cluster.uid()) {
                        continue;
                    }
                    getRemoteManager(member).tell(SingletonManagerMessage.newBuilder()
                            .setType(SingletonManagerMessage.Type.INSTANCE_READY)
                            .setUid(cluster.uid())
                            .build());
                }
                ActorUnsafe.tellInline(self(), SingletonManagerMessage.newBuilder()
                        .setType(SingletonManagerMessage.Type.INSTANCE_ACK)
                        .setUid(cluster.uid())
                        .build());
            }, delay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
            Set<Long> ackedUidSet = new HashSet<>();
            return log(receive(msg -> {
                switch (msg.getType()) {
                    case INSTANCE_ACK -> {
                        ackedUidSet.add(msg.getUid());
                        if (ackedUidSet.size() >= availableMembers.size()) {
                            // ensure receive ack from all available instead of left members
                            ackedUidSet.removeIf(l -> !availableMembers.containsKey(l));
                            if (ackedUidSet.size() == availableMembers.size()) {
                                sc.cancel(false);
                                return instanceRunning();
                            }
                        }
                        return same();
                    }
                    case INSTANCE_READY -> {
                        // TODO split brain?
                        LOG.warn("Receive instance ready while current state is also");
                        assert leaderMember != null;
                        assert leaderMember.uid() == cluster.uid();
                        if (cluster.uid() <= msg.getUid()) {
                            return same();
                        } else {
                            sc.cancel(false);
                            return unservableFollower();
                        }
                    }
                    default -> {
                        return unhandled();
                    }
                }
            }, signal -> {
                if (!(signal instanceof StateChange)) {
                    return unhandled();
                }
                switch ((StateChange) signal) {
                    case UNSERVABLE -> {
                        sc.cancel(false);
                        return unservableFollower();
                    }
                    case LEADER_CHANGED -> {
                        // TODO split brain?
                        LOG.warn("Unexpected signal: {}, maybe a bug, current leader member is " +
                                 "{}, self state is instance prepare", signal, leaderMember);
                        sc.cancel(false);
                        assert leaderMember != null;
                        tryLeaderAck(getRemoteManager(leaderMember));
                        return servableFollower();
                    }
                    default -> {
                        LOG.error("Unexpected signal: {}, maybe a bug, current leader member is " +
                                  "{}", signal, leaderMember);
                        context().system().systemFailure(new IllegalStateException("Unexpected " +
                                                                                   "signal: " + signal + ", maybe a bug"));
                    }
                }
                return same();
            }), LOG, LOG_LEVEL);
        }

        private Behavior<SingletonManagerMessage> instanceRunning() {
            LOG.info("Manager state changed to INSTANCE_RUNNING");
            realInstance = instance = context().startChild(singletonCreator, instanceName());
            context().watch(instance, List.of(SystemEvent.ActorStopped.class));
            cluster.updateMetaInfo(BuiltinMetaKeys.SINGLETONS.update(s ->
                    s.toBuilder().putStates(name, true).build()));
            flushBuffer();
            return log(receive(msg -> {
                switch (msg.getType()) {
                    case INSTANCE_ACK -> {
                        return same();
                    }
                    case INSTANCE_READY -> {
                        // TODO split brain?
                        LOG.warn("Receive instance ready while current state is instance running");
                        assert leaderMember != null;
                        assert leaderMember.uid() == cluster.uid();
                        if (cluster.uid() <= msg.getUid()) {
                            return same();
                        } else {
                            return instanceStopping();
                        }
                    }
                    default -> {
                        return unhandled();
                    }
                }
            }, signal -> {
                assert instance.address().address().equals(context().system().publishAddress());
                assert leaderMember != null && leaderMember.uid() == cluster.uid();
                if (signal instanceof SystemEvent.ActorStopped(var ref) && ref.equals(instance)) {
                    LOG.warn("Instance [{}] stopped unexpectedly, restart", ref);
                    realInstance = instance = context().startChild(singletonCreator,
                            instanceName());
                    context().watch(instance, List.of(SystemEvent.ActorStopped.class));
                }
                if (!(signal instanceof StateChange)) {
                    return unhandled();
                }
                switch ((StateChange) signal) {
                    case UNSERVABLE -> {
                        // unservableInstanceStopping
                        return instanceStopping();
                    }
                    case LEADER_REMOVED -> {
                        // servableInstanceStopping
                        return instanceStopping();
                    }
                    case LEADER_ADDED, LEADER_CHANGED -> {
                        // servableInstanceStopping, TODO split brain?
                        return instanceStopping();
                    }
                    case INSTANCE_STARTED -> {
                        return Behaviors.same();
                    }
                    default -> {
                        LOG.error("Unexpected signal: {}, maybe a bug, current leader member is " +
                                  "{}, self state is instance ready", signal, leaderMember);
                        context().system().systemFailure(new IllegalStateException("Unexpected " +
                                                                                   "signal: " + signal + ", maybe a bug"));
                    }
                }
                return same();
            }), LOG, LOG_LEVEL);
        }

        private Behavior<SingletonManagerMessage> instanceStopping() {
            ActorRef<T> instance = this.instance;
            ActorUnsafe.signal(instance, stopSignal);
            realInstance = this.instance = null;
            long timeout = config.stopTimeout().toMillis();
            var future = context().dispatcher().schedule(() -> {
                LOG.warn("Instance stop timeout, force shutting down");
                context().system().stop(instance);
            }, timeout, TimeUnit.MILLISECONDS);
            List<Signal> signalBuffer = new ArrayList<>();
            if (servable) {
                return unservableInstanceStopping(future, signalBuffer);
            } else {
                return servableInstanceStopping(future, signalBuffer);
            }
        }

        private Behavior<SingletonManagerMessage> unservableInstanceStopping(ScheduledFuture<?> future, List<Signal> signalBuffer) {
            LOG.info("Manager state changed to UNSERVABLE_INSTANCE_STOPPING");
            return log(receive(msg -> {
                if (msg.getType() == SingletonManagerMessage.Type.INSTANCE_READY) {
                    LOG.warn("Receive instance ready but self Instance is still stopping and " +
                             "unservable, ignore msg {}", ProtobufUtil.toString(msg));
                    return same();
                }
                return unhandled();
            }, signal -> {
                if (signal instanceof SystemEvent.ActorStopped(var ref) && ref.equals(instance)) {
                    future.cancel(false);
                    var consumedBuffer = Behaviors.consumeBuffer(unservableFollower());
                    signalBuffer.forEach(consumedBuffer::addSignal);
                    return consumedBuffer.toBehavior();
                }
                if (!(signal instanceof StateChange)) {
                    return unhandled();
                }
                if (signal == StateChange.SERVABLE) {
                    return servableInstanceStopping(future, signalBuffer);
                }
                signalBuffer.add(signal);
                return same();
            }), LOG, LOG_LEVEL);
        }

        private Behavior<SingletonManagerMessage> servableInstanceStopping(ScheduledFuture<?> future, List<Signal> signalBuffer) {
            LOG.info("Manager state changed to SERVABLE_INSTANCE_STOPPING");
            return log(receive(msg -> {
                if (msg.getType() == SingletonManagerMessage.Type.INSTANCE_READY) {
                    LOG.warn("Receive instance ready but self Instance is still stopping, ignore " +
                             "msg {}", ProtobufUtil.toString(msg));
                    return same();
                }
                return unhandled();
            }, signal -> {
                if (signal instanceof SystemEvent.ActorStopped(var ref) && ref.equals(instance)) {
                    future.cancel(false);
                    var consumedBuffer = Behaviors.consumeBuffer(servableFollower());
                    signalBuffer.forEach(consumedBuffer::addSignal);
                    return consumedBuffer.toBehavior();
                }
                if (!(signal instanceof StateChange)) {
                    return unhandled();
                }
                if (signal == StateChange.UNSERVABLE) {
                    return unservableInstanceStopping(future, signalBuffer);
                }
                signalBuffer.add(signal);
                return same();
            }), LOG, LOG_LEVEL);
        }

        private void tryLeaderAck(ActorRef<SingletonManagerMessage> ref) {
            ref.tell(SingletonManagerMessage.newBuilder()
                    .setType(SingletonManagerMessage.Type.INSTANCE_ACK)
                    .setUid(cluster.uid())
                    .build());
        }

        private ActorRef<SingletonManagerMessage> getRemoteManager(ClusterMember member) {
            var address = ActorAddress.create(member.system(),
                    member.address(), managerName());
            try {
                return context().system().get(address, MANAGER_MSG_TYPE);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new ActorRuntimeException(e);
            }
        }


        @Override
        public MsgType<SingletonManagerMessage> msgType() {
            return MsgType.of(SingletonManagerMessage.class);
        }
    }
}
