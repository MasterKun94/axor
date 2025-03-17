package io.masterkun.kactor.cluster.membership;

import io.masterkun.kactor.api.AbstractActor;
import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.api.ActorContext;
import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.api.Behavior;
import io.masterkun.kactor.api.Behaviors;
import io.masterkun.kactor.cluster.LocalMemberState;
import io.masterkun.kactor.cluster.MemberState;
import io.masterkun.kactor.cluster.config.MembershipConfig;
import io.masterkun.kactor.commons.collection.LongObjectHashMap;
import io.masterkun.kactor.commons.collection.LongObjectMap;
import io.masterkun.kactor.exception.ActorException;
import io.masterkun.kactor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class MembershipActor extends AbstractActor<MembershipMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MembershipActor.class);

    private final MembershipConfig config;
    private final MemberManager memberManager;
    private final FailureDetector failureDetector;
    private final SplitBrainResolver splitBrainResolver;
    private final List<MembershipListener> listeners = new ArrayList<>();
    private MetaInfo selfMetaInfo = MetaInfo.EMPTY;
    private LocalMemberState localMemberState = LocalMemberState.NONE;
    private ScheduledFuture<?> tmpScheduledFuture;

    public MembershipActor(ActorContext<MembershipMessage> context,
                           MembershipConfig config,
                           SplitBrainResolver splitBrainResolver) {
        super(context);
        this.config = config;
        this.memberManager = new MemberManager(
                MemberIdGenerator.create(context.self().address()).nextId(),
                self(),
                config.memberManage(),
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

    private void updateLocalMemberState(LocalMemberState state) {
        if (localMemberState != state) {
            this.localMemberState = state;
            LOG.info("Local member state change to: {}", localMemberState);
            for (MembershipListener listener : listeners) {
                listener.onLocalStateChange(state);
            }
        }
    }

    private Behavior<MembershipMessage> commonBehavior(MembershipMessage msg) {
        if (msg instanceof MembershipMessage.AddListener(var listener, var inc)) {
            listeners.add(listener);
            memberManager.addListener(listener, inc);
            listener.onLocalStateChange(localMemberState);
            return Behaviors.same();
        }
        if (msg instanceof MembershipMessage.RemoveListener(var listener)) {
            listeners.remove(listener);
            memberManager.removeListener(listener);
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
        tmpScheduledFuture = context().executor().scheduleWithFixedDelay(
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
                if (splitBrainResolver.getLocalMemberState() == LocalMemberState.UP ||
                        splitBrainResolver.getLocalMemberState() == LocalMemberState.WEEKLY_UP) {
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
        tmpScheduledFuture = context().executor().scheduleWithFixedDelay(() -> {
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
            if (msg == MembershipMessage.FORCE_LEAVE) {
                return left();
            }
            if (msg instanceof Gossip gossip) {
                for (MemberEvent event : gossip.events()) {
                    if (event.action() == MemberAction.LEAVE_ACK) {
                        map.remove(event.uid());
                    }
                }
                if (map.isEmpty()) {
                    return left();
                }
                return Behaviors.same();
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
}
