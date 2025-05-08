package io.axor.cluster.membership;

import io.axor.api.AbstractActor;
import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.cluster.ClusterEvent;
import io.axor.cluster.ClusterMember;
import io.axor.cluster.LocalMemberState;
import io.axor.cluster.MemberState;
import io.axor.cluster.config.MembershipConfig;
import io.axor.commons.collection.LongObjectHashMap;
import io.axor.commons.collection.LongObjectMap;
import io.axor.exception.ActorException;
import io.axor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class MembershipActor extends AbstractActor<MembershipMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MembershipActor.class);

    private final MembershipConfig config;
    private final MemberManager memberManager;
    private final FailureDetector failureDetector;
    private final SplitBrainResolver splitBrainResolver;
    private final Map<ActorAddress, MembershipListener> listeners = new HashMap<>();
    private MetaInfo selfMetaInfo = MetaInfo.EMPTY;
    private LocalMemberState localMemberState = LocalMemberState.NONE;
    private ScheduledFuture<?> tmpScheduledFuture;

    public MembershipActor(long uid,
                           ActorContext<MembershipMessage> context,
                           MembershipConfig config,
                           SplitBrainResolver splitBrainResolver) {
        super(context);
        this.config = config;
        this.memberManager = new MemberManager(uid, self(), config.memberManage(),
                context.system()::systemFailure);
        this.failureDetector = FailureDetector.create(
                config.failureDetect(), memberManager, context);
        this.splitBrainResolver = splitBrainResolver;
        memberManager.addListener(splitBrainResolver, true);
    }

    @Override
    protected Behavior<MembershipMessage> initialBehavior() {
        return ready();
    }

    @Override
    public void postStop() {
        LOG.info("Local member stopped");
        for (MembershipListener listener : listeners.values()) {
            listener.onLocalMemberStopped();
        }
        listeners.clear();
    }

    private void updateLocalMemberState(LocalMemberState state) {
        if (localMemberState != state) {
            this.localMemberState = state;
            LOG.info("Local member state change to: {}", localMemberState);
            for (MembershipListener listener : listeners.values()) {
                listener.onLocalStateChange(state);
            }
        }
    }

    private Behavior<MembershipMessage> commonBehavior(MembershipMessage msg) {
        if (msg instanceof MembershipMessage.AddListener(var listener, var inc)) {
            ActorMembershipListener theListener = new ActorMembershipListener(listener);
            listeners.put(listener.address(), theListener);
            memberManager.addListener(theListener, inc);
            theListener.onLocalStateChange(localMemberState);
            return Behaviors.same();
        }
        if (msg instanceof MembershipMessage.RemoveListener(var listener)) {
            MembershipListener removed = listeners.remove(listener.address());
            if (removed != null) {
                memberManager.removeListener(removed);
            }
            return Behaviors.same();
        }
        if (msg instanceof MembershipMessage.UpdateMetaInfo(List<MetaKey.Action> actions)) {
            selfMetaInfo = selfMetaInfo.transform(actions);
            long selfUid = memberManager.getSelfUid();
            var selfMember = new Member(selfUid, selfMetaInfo, context().self());
            var clock = memberManager.internalIncAndGetClock();
            var event = new MemberEvent(selfMember, MemberAction.UPDATE, clock);
            memberManager.gossipEvent(Gossip.of(event, selfUid));
            return Behaviors.same();
        }
        if (msg == MembershipMessage.FORCE_LEAVE) {
            return left();
        }
        return Behaviors.unhandled();
    }

    private Behavior<MembershipMessage> ready() {
        updateLocalMemberState(LocalMemberState.NONE);
        return Behaviors.receiveMessage((ctx, msg) -> {
            if (msg == MembershipMessage.JOIN) {
                return joining();
            }
            return commonBehavior(msg);
        });
    }

    private Behavior<MembershipMessage> joining() {
        updateLocalMemberState(LocalMemberState.JOINING);
        var system = context().system();
        List<ActorRef<Gossip>> seeds = new ArrayList<>(config.join().seeds().size());
        var selfAddr = context().self().address();
        for (URI uri : config.join().seeds()) {
            var address = ActorAddress.create(uri, selfAddr.system(),
                    selfAddr.port(), selfAddr.name());
            ActorRef<Gossip> actor;
            try {
                actor = system.get(address, MsgType.of(MembershipMessage.class))
                        .cast(MsgType.of(Gossip.class));
            } catch (ActorException e) {
                LOG.warn("Ignore seed {} because error", address, e);
                continue;
            }
            if (actor.equals(context().self())) {
                continue;
            }
            seeds.add(actor);
        }
        if (seeds.isEmpty()) {
            LOG.warn("No available seeds found");
        } else {
            LOG.info("Join seeds are: {}", seeds);
        }
        long selfUid = memberManager.getSelfUid();
        Supplier<MemberEvent> eventSupplier = () -> new MemberEvent(
                new Member(selfUid, selfMetaInfo, context().self()),
                MemberAction.JOIN,
                memberManager.internalIncAndGetClock()
        );
        memberManager.gossipEvent(Gossip.of(eventSupplier.get(), selfUid));
        tmpScheduledFuture = context().scheduler().scheduleWithFixedDelay(
                () -> {
                    var gossip = Gossip.of(eventSupplier.get(), selfUid, true);
                    seeds.forEach(actor -> actor.tell(gossip, self()));
                }, 0, config.join().reqInterval().toMillis(), TimeUnit.MILLISECONDS);
        return Behaviors.receiveMessage((ctx, msg) -> {
            if (msg == MembershipMessage.LEAVE) {
                return leaving();
            }
            if (msg instanceof Gossip.PushedEvents gossip) {
                memberManager.gossipEvent(gossip);
                if (splitBrainResolver.getLocalMemberState() == LocalMemberState.HEALTHY ||
                    splitBrainResolver.getLocalMemberState() == LocalMemberState.UNHEALTHY) {
                    return up();
                }
                return Behaviors.same();
            }
            return commonBehavior(msg);
        });
    }

    private Behavior<MembershipMessage> up() {
        tmpScheduledFuture.cancel(false);
        tmpScheduledFuture = null;
        failureDetector.start();
        updateLocalMemberState(splitBrainResolver.getLocalMemberState());
        return Behaviors.receiveMessage((ctx, msg) -> {
            if (msg == MembershipMessage.LEAVE) {
                return leaving();
            }
            if (msg instanceof Gossip gossip) {
                failureDetector.heartbeatFrom(gossip.sender());
                memberManager.gossipEvent(gossip);
                updateLocalMemberState(splitBrainResolver.getLocalMemberState());
                return Behaviors.same();
            }
            return commonBehavior(msg);
        });
    }

    private Behavior<MembershipMessage> leaving() {
        failureDetector.stop();
        updateLocalMemberState(LocalMemberState.LEAVING);
        LongObjectMap<Member> map = memberManager.getMembers(MemberState.UP).collect(
                LongObjectHashMap::new,
                (m, e) -> m.put(e.uid(), e),
                LongObjectMap::putAll);
        long timeout = System.currentTimeMillis() + config.leave().timeout().toMillis();
        tmpScheduledFuture = context().scheduler().scheduleWithFixedDelay(() -> {
            if (System.currentTimeMillis() > timeout) {
                context().self().tell(MembershipMessage.FORCE_LEAVE, self());
                return;
            }
            var selfMember = memberManager.getMember(memberManager.getSelfUid());
            var clock = memberManager.internalIncAndGetClock();
            var event = new MemberEvent(selfMember, MemberAction.LEAVE, clock);
            var leave = Gossip.of(event, selfMember.uid());
            for (Member value : map.values()) {
                value.actor().tell(leave, self());
            }
        }, 0, config.leave().reqInterval().toMillis(), TimeUnit.MILLISECONDS);
        return Behaviors.receiveMessage((ctx, msg) -> {
            if (msg instanceof Gossip gossip) {
                for (MemberEvent event : gossip.events()) {
                    if (event.action() == MemberAction.LEAVE_ACK) {
                        map.remove(gossip.sender());
                    } else if (event.action() == MemberAction.FAIL ||
                               event.action() == MemberAction.STRONG_SUSPECT) {
                        map.remove(event.uid());
                    } else if (event.action() == MemberAction.LEAVE) {
                        Member removed = map.remove(event.uid());
                        if (removed != null) {
                            removed.actor().tell(Gossip.of(
                                    new MemberEvent(removed, MemberAction.LEAVE_ACK,
                                            memberManager.getClock(removed.uid())),
                                    memberManager.getSelfUid()
                            ), sender());
                        }
                    }
                }
                if (map.isEmpty()) {
                    return left();
                }
                return Behaviors.same();
            }
            if (msg == MembershipMessage.FORCE_LEAVE) {
                return commonBehavior(msg);
            }
            if (msg instanceof MembershipMessage.RemoveListener) {
                return commonBehavior(msg);
            }
            return Behaviors.unhandled();
        });
    }

    private Behavior<MembershipMessage> left() {
        tmpScheduledFuture.cancel(false);
        tmpScheduledFuture = null;
        updateLocalMemberState(LocalMemberState.LEFT);
        return Behaviors.stop();
    }

    @Override
    public MsgType<MembershipMessage> msgType() {
        return MsgType.of(MembershipMessage.class);
    }

    private static class ActorMembershipListener implements MembershipListener {
        private final ActorRef<ClusterEvent> ref;

        private ActorMembershipListener(ActorRef<ClusterEvent> ref) {
            this.ref = ref;
        }

        @Override
        public void onLocalStateChange(LocalMemberState currentState) {
            ref.tell(new ClusterEvent.LocalStateChange(currentState));
        }

        @Override
        public void onLocalMemberStopped() {
            ref.tell(new ClusterEvent.LocalMemberStopped());
        }

        @Override
        public void onMemberStateChange(Member member, MemberState from, MemberState to) {
            ref.tell(new ClusterEvent.MemberStateChanged(ClusterMember.of(member), from, to));
        }

        @Override
        public void onMemberUpdate(Member from, Member to) {
            ref.tell(new ClusterEvent.MemberMetaInfoChanged(ClusterMember.of(to), from.metaInfo()));
        }
    }
}
