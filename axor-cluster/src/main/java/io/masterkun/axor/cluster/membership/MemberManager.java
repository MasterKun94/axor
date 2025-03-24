package io.masterkun.axor.cluster.membership;

import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.cluster.MemberState;
import io.masterkun.axor.cluster.config.MemberManageConfig;
import io.masterkun.axor.commons.collection.LongObjectHashMap;
import io.masterkun.axor.commons.collection.LongObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class MemberManager {
    private static final Logger LOG = LoggerFactory.getLogger(MemberManager.class);
    private final long selfUid;
    private final ActorRef<?> self;
    private final MemberManageConfig config;
    private final Consumer<Throwable> failureHook;
    private final LongObjectMap<MemberHolder> allMembers = new LongObjectHashMap<>();
    private final LongObjectMap<MemberEvent> publishEvents = new LongObjectHashMap<>();
    private final LongObjectMap<LongObjectMap<MemberEvent>> sendEvents = new LongObjectHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final PublishMembers publishMembers;
    private long selfClock;
    private int sendTargetSize = 0;

    public MemberManager(long selfUid,
                         ActorRef<?> self,
                         MemberManageConfig config,
                         Consumer<Throwable> failureHook) {
        this.selfUid = selfUid;
        this.self = self;
        this.config = config;
        this.failureHook = failureHook;
        this.selfClock = 1;
        this.publishMembers = new PublishMembers(config, selfUid, allMembers);
    }

    long getSelfUid() {
        return selfUid;
    }

    VectorClock getClock() {
        return VectorClock.wrap(selfUid, selfClock);
    }

    VectorClock getClock(long uid) {
        return Objects.requireNonNull(allMembers.get(uid).clock);
    }

    Member getMember(long uid) {
        MemberHolder holder = allMembers.get(uid);
        return holder == null ? null : holder.member;
    }

    VectorClock internalIncAndGetClock() {
        return VectorClock.wrap(0, ++selfClock);
    }

    public VectorClock incAndGetClock() {
        return VectorClock.wrap(selfUid, ++selfClock);
    }

    public void gossipEvent(Gossip gossip) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Receive Gossip event: {}", gossip);
        }
        long senderUid = gossip.sender();
        // handle event
        for (MemberEvent event : gossip.events()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Process member event: {}", event);
            }
            var member = event.member();
            var action = event.action();
            var clock = event.clock();
            assert clock.array()[0] == 0;

            switch (action) {
                case JOIN, HEARTBEAT, UPDATE ->
                        memberJoinOrUpdate(member, clock, action, senderUid);
                case LEAVE -> memberLeave(member, clock, senderUid);
                case SUSPECT -> memberSuspect(member, clock, senderUid);
                case STRONG_SUSPECT -> memberStrongSuspect(member, clock, senderUid);
                case FAIL -> memberFail(member, clock, senderUid);
                case REMOVE -> {
                    memberRemove(member, clock, senderUid);
                }
                default -> LOG.warn("Ignore event: {}", event);
            }
        }

        // pong
        if (gossip.ping()) {
            boolean needPull = false;
            for (MemberClock clock : gossip.clocks()) {
                MemberHolder holder = allMembers.get(clock.uid());
                if (holder == null) {
                    needPull = true;
                    break;
                }
                if (holder.clock.isEarlierThan(clock.clock())) {
                    needPull = true;
                    break;
                }
            }
            var events = sendEvents.remove(senderUid);
            MemberHolder holder = allMembers.get(senderUid);
            if (holder == null) {
                LOG.warn("Sender member not found: {}", senderUid);
            } else {
                Gossip pong = events == null || events.isEmpty() ?
                        Gossip.pong(selfUid, needPull) :
                        Gossip.of(events.values(), selfUid, needPull);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Pong back to sender: {}, msg: {}", holder.member, pong);
                }
                holder.member.actor().tell(pong, self);
            }
        }
        if (gossip.pull()) {
            for (var entry : allMembers.entries()) {
                if (entry.key() == senderUid) {
                    continue;
                }
                MemberHolder holder = entry.value();
                sendEvent(senderUid, holder, holder.state.BIND_ACTION);
            }
        }
        if (LOG.isDebugEnabled()) {
            Map<MemberState, Integer> counters = new HashMap<>();
            for (MemberHolder holder : allMembers.values()) {
                counters.compute(holder.state, (k, v) -> v == null ? 1 : v + 1);
            }
            LOG.debug("Current clock: {}, members: {}", selfClock, counters);
            if (!publishEvents.isEmpty()) {
                LOG.debug("Gossip publish events: {}", publishEvents.values());
            }
            for (var entry : sendEvents.entries()) {
                if (entry.value().isEmpty()) {
                    continue;
                }
                LOG.debug("Gossip send events to {} : {}", entry.key(), entry.value());
            }
        }
        // publish gossip
        if (!publishEvents.isEmpty()) {
            MemberEvent[] array = publishEvents.values().toArray(MemberEvent[]::new);
            publishEvents.clear();
            Gossip publishGossip = Gossip.of(Unsafe.wrap(array), selfUid);
            for (Member member : publishMembers) {
                if (member.uid() == senderUid) {
                    continue;
                }
                if (sendTargetSize > 0) {
                    var events = sendEvents.get(member.uid());
                    if (events != null && !events.isEmpty()) {
                        for (MemberEvent memberEvent : array) {
                            events.putIfAbsent(memberEvent.uid(), memberEvent);
                        }
                        var arr = events.values().toArray(MemberEvent[]::new);
                        events.clear();
                        Gossip msg = Gossip.of(Unsafe.wrap(arr), selfUid);
                        member.actor().tell(msg, self);
                        sendTargetSize--;
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Publish gossip {} to member {}", publishGossip, member);
                        }
                        member.actor().tell(publishGossip, self);
                    }
                } else {
                    member.actor().tell(publishGossip, self);
                }
            }
        }
        // send gossip
        if (sendTargetSize > 0) {
            for (var entry : sendEvents.entries()) {
                var value = entry.value();
                if (value.isEmpty()) {
                    continue;
                }
                MemberEvent[] array = value.values().toArray(MemberEvent[]::new);
                value.clear();
                sendTargetSize--;
                Gossip sendGossip = Gossip.of(Unsafe.wrap(array), selfUid);
                MemberHolder holder = allMembers.get(entry.key());
                if (holder == null) {
                    LOG.warn("Member not found: {}", entry.key());
                    continue;
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Send gossip {} to member {}", sendGossip, holder.member);
                }
                holder.member.actor().tell(sendGossip, self);
            }
        }
        assert sendTargetSize == 0;
    }

    public boolean addListener(Listener listener, boolean listenIncremental) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            if (listenIncremental) {
                return true;
            }
            for (MemberHolder value : allMembers.values()) {
                listener.onMemberStateChange(value.member, MemberState.NONE, value.state);
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean removeListener(Listener listener) {
        return listeners.remove(listener);
    }

    public Stream<Member> getMembers(MemberState memberState) {
        return allMembers.values().stream()
                .filter(m -> m.state == memberState)
                .map(m -> m.member);
    }

    private void sendEvent(long sendTo, MemberHolder holder, MemberAction action) {
        var map = sendEvents.computeIfAbsent(sendTo, l -> new LongObjectHashMap<>());
        if (map.isEmpty()) {
            sendTargetSize++;
        }
        map.put(holder.member.uid(), new MemberEvent(holder.member, action, holder.clock));
    }

    private void memberUpdate(MemberHolder holder,
                              Member newMember) {
        Member oldMember = holder.member;
        holder.member = newMember;
        MemberEvent event = new MemberEvent(holder.member, MemberAction.UPDATE, holder.clock);
        publishEvents.put(newMember.uid(), event);
        for (Listener listener : listeners) {
            listener.onMemberUpdate(oldMember, newMember);
        }
    }

    private void memberStateChange(MemberHolder holder,
                                   MemberState from,
                                   MemberAction action,
                                   MemberState to) {
        Member member = holder.member;
        assert from != to;
        long uid = member.uid();
        if (from == MemberState.NONE) {
            allMembers.put(uid, holder);
        } else {
            if (from == MemberState.UP) {
                publishMembers.upMemberChanged();
            }
        }
        holder.state = to;
        holder.stateChangeTime = System.currentTimeMillis();
        if (to == MemberState.UP) {
            publishMembers.upMemberChanged();
        }
        MemberEvent event = new MemberEvent(member, action, holder.clock);
        publishEvents.put(uid, event);
        for (Listener listener : listeners) {
            listener.onMemberStateChange(member, from, to);
        }
    }

    private MemberHolder holder(Member member, VectorClock clock) {
        var holder = new MemberHolder();
        holder.member = member;
        holder.clock = clock;
        holder.state = MemberState.NONE;
        return holder;
    }

    private void memberJoinOrUpdate(Member member, VectorClock clock, MemberAction action,
                                    long senderUid) {
        long uid = member.uid();
        MemberHolder holder = allMembers.get(uid);
        if (holder == null) {
            holder = holder(member, clock);
        } else if (holder.clock.isLaterThan(clock)) {
            LOG.debug("Discarding member {} join because vectorClock expired", member);
            sendEvent(senderUid, holder, holder.state.BIND_ACTION);
            return;
        }
        assert holder.member.uid() == uid;
        holder.clock = holder.clock.merge(clock);
        switch (holder.state) {
            case NONE:
            case SUSPICIOUS:
            case DOWN:
                memberStateChange(holder, holder.state, action, MemberState.UP);
                break;
            case UP:
                break;
            case LEFT:
                holder.clock = holder.clock.merge(incAndGetClock());
                sendEvent(senderUid, holder, MemberAction.FAIL);
                break;
            default:
                throw new IllegalStateException("Unexpected state: " + holder.state);
        }
        if (!holder.member.metaEquals(member)) {
            memberUpdate(holder, member);
        }
    }

    private void memberLeave(Member member, VectorClock clock, long senderUid) {
        long uid = member.uid();
        MemberHolder holder = allMembers.get(uid);
        if (holder == null) {
            holder = holder(member, clock);
        } else if (holder.clock.isLaterThan(clock)) {
            LOG.debug("Discarding member {} left because vectorClock expired", member);
            sendEvent(senderUid, holder, holder.state.BIND_ACTION);
            return;
        }
        holder.clock = holder.clock.merge(clock);
        switch (holder.state) {
            case NONE:
            case UP:
            case SUSPICIOUS:
            case DOWN:
                memberStateChange(holder, holder.state, MemberAction.LEAVE, MemberState.LEFT);
                break;
            default:
                break;
        }
        if (uid != selfUid) {
            sendEvent(uid, holder, MemberAction.LEAVE_ACK);
        }
    }

    private void memberSuspect(Member member, VectorClock clock, long senderUid) {
        long uid = member.uid();
        MemberHolder holder = allMembers.get(uid);
        if (uid == selfUid) {
            holder.clock = holder.clock
                    .merge(internalIncAndGetClock());
            sendEvent(senderUid, allMembers.get(selfUid), MemberAction.UPDATE);
            return;
        }
        if (holder == null) {
            holder = holder(member, clock);
        } else if (holder.clock.isLaterThan(clock)) {
            LOG.debug("Discarding member {} suspect because vectorClock expired", member);
            sendEvent(senderUid, holder, holder.state.BIND_ACTION);
            return;
        }
        holder.clock = holder.clock.merge(clock);
        switch (holder.state) {
            case NONE:
            case UP:
            case DOWN:
                memberStateChange(holder, holder.state, MemberAction.SUSPECT,
                        MemberState.SUSPICIOUS);
                break;
            case SUSPICIOUS:
                break;
            case LEFT:
                sendEvent(senderUid, holder, MemberAction.FAIL);
                break;
            default:
                throw new IllegalStateException("Unexpected state: " + holder.state);
        }
    }

    private void memberStrongSuspect(Member member, VectorClock clock, long senderUid) {
        long uid = member.uid();
        MemberHolder holder = allMembers.get(uid);
        if (uid == selfUid) {
            holder.clock = holder.clock
                    .merge(internalIncAndGetClock());
            sendEvent(senderUid, allMembers.get(selfUid), MemberAction.UPDATE);
            return;
        }
        if (holder == null) {
            holder = holder(member, clock);
        } else if (holder.clock.isLaterThan(clock)) {
            LOG.debug("Discarding member {} strong_suspect because vectorClock expired", member);
            sendEvent(senderUid, holder, holder.state.BIND_ACTION);
            return;
        }
        holder.clock = holder.clock.merge(clock);
        switch (holder.state) {
            case NONE:
            case UP:
            case SUSPICIOUS:
                memberStateChange(holder, holder.state, MemberAction.STRONG_SUSPECT,
                        MemberState.DOWN);
                break;
            case DOWN:
                break;
            case LEFT:
                sendEvent(senderUid, holder, MemberAction.FAIL);
                break;
            default:
                throw new IllegalStateException("Unexpected state: " + holder.state);
        }
    }

    private void memberFail(Member member, VectorClock clock, long ignoreSenderUid) {
        long uid = member.uid();
        MemberHolder holder = allMembers.get(uid);
        if (uid == selfUid) {
            failureHook.accept(new RuntimeException("Self member failed by force"));
        }
        if (holder == null) {
            holder = holder(member, clock);
        }
        holder.clock = holder.clock.merge(clock);
        switch (holder.state) {
            case NONE:
            case UP:
            case SUSPICIOUS:
            case DOWN:
                memberStateChange(holder, holder.state, MemberAction.FAIL, MemberState.LEFT);
                break;
            case LEFT:
                break;
            default:
                throw new IllegalStateException("Unexpected state: " + holder.state);
        }
    }

    private void memberRemove(Member member, VectorClock clock, long senderUid) {
        long uid = member.uid();
        if (uid == selfUid) {
            failureHook.accept(new RuntimeException("Self member failed by force remove"));
        }
        MemberHolder holder = allMembers.remove(uid);
        if (holder == null) {
            return;
        }
        holder.clock = holder.clock.merge(clock);
        switch (holder.state) {
            case NONE:
            case UP:
            case SUSPICIOUS:
            case DOWN:
                LOG.warn("Removing a member with state is not left: {}", member);
            case LEFT:
                memberStateChange(holder, holder.state, MemberAction.REMOVE, MemberState.REMOVED);
                break;
            default:
                throw new IllegalStateException("Unexpected state: " + holder.state);
        }
    }

    public interface Listener {

        default void onMemberUpdate(Member from, Member to) {
        }

        default void onMemberStateChange(Member member, MemberState from, MemberState to) {
        }
    }

    @VisibleForTesting
    static final class MemberHolder {
        Member member;
        ActorRef<Gossip> actor;
        VectorClock clock;
        MemberState state;
        long stateChangeTime;
    }

    @VisibleForTesting
    static class PublishMembers implements Iterable<Member> {
        private final List<Member> actors = new ArrayList<>();
        private final MemberManageConfig config;
        private final long selfUid;
        private final Map<?, MemberHolder> allMembers;
        private int numActorsToPublish;
        private int publishIdx;
        private int size;
        private boolean upMemberChanged = false;

        PublishMembers(MemberManageConfig config, long selfUid, Map<?, MemberHolder> allMembers) {
            this.config = config;
            this.selfUid = selfUid;
            this.allMembers = allMembers;
        }

        void upMemberChanged() {
            this.upMemberChanged = true;
        }

        @NotNull
        @Override
        public Iterator<Member> iterator() {
            if (upMemberChanged) {
                actors.clear();
                for (MemberHolder value : allMembers.values()) {
                    if (value.state != MemberState.UP) {
                        continue;
                    }
                    long uid = value.member.uid();
                    if (uid == selfUid) {
                        continue;
                    }
                    actors.add(value.member);
                }
                size = actors.size();
                numActorsToPublish = Math.min(size, Math.max(config.publishNumMin(),
                        (int) Math.rint(actors.size() * config.publishRate())));
                upMemberChanged = false;
            }
            return new Iterator<>() {
                private int num = 0;

                @Override
                public boolean hasNext() {
                    return num < numActorsToPublish;
                }

                @Override
                public Member next() {
                    num++;
                    int idx = ++publishIdx;
                    if (idx >= size) {
                        idx = publishIdx = 0;
                    }
                    return actors.get(idx);
                }
            };
        }
    }
}
