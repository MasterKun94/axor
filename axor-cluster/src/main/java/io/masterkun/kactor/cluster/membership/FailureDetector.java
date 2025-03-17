package io.masterkun.kactor.cluster.membership;

import io.masterkun.kactor.api.ActorContext;
import io.masterkun.kactor.cluster.MemberState;
import io.masterkun.kactor.cluster.config.FailureDetectConfig;
import io.masterkun.kactor.commons.collection.LongObjectHashMap;
import io.masterkun.kactor.commons.collection.LongObjectMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public sealed interface FailureDetector {
    static FailureDetector create(FailureDetectConfig config,
                                  MemberManager memberManager,
                                  ActorContext<?> context) {
        return config.enabled() ?
                new FailureDetectorImpl(config, memberManager, context) :
                new NoopFailureDetector();
    }

    void start();

    void heartbeatFrom(long uid);

    void stop();
}

final class NoopFailureDetector implements FailureDetector {

    @Override
    public void start() {

    }

    @Override
    public void heartbeatFrom(long uid) {
        // do nothing
    }

    @Override
    public void stop() {
        // do nothing
    }
}

final class FailureDetectorImpl implements FailureDetector {
    private static final Logger LOG = LoggerFactory.getLogger(FailureDetectorImpl.class);
    private final long memberPingTimeoutMillis;
    private final long memberDownTimeoutMillis;
    private final long memberFailTimeoutMillis;
    private final long memberRemoveTimeoutMillis;
    private final long checkIntervalMillis;
    private final long pingIntervalMillis;
    private final MemberManager memberManager;
    private final ActorContext<?> context;
    private final List<ScheduledFuture<?>> scheduleList = new ArrayList<>();
    private final LongObjectMap<ValueHolder> heartbeats = new LongObjectHashMap<>();
    private final List<Long> servableMemberUidList = new ArrayList<>();
    private int pingOff = 0;

    FailureDetectorImpl(FailureDetectConfig config,
                        MemberManager memberManager,
                        ActorContext<?> context) {
        this.memberPingTimeoutMillis = config.memberPingTimeout().toMillis();
        this.memberDownTimeoutMillis = config.memberDownTimeout().toMillis();
        this.memberFailTimeoutMillis = config.memberFailTimeout().toMillis();
        this.memberRemoveTimeoutMillis = config.memberRemoveTimeout().toMillis();
        this.checkIntervalMillis = config.failCheckInterval().toMillis();
        this.pingIntervalMillis = config.pingInterval().toMillis();
        this.memberManager = memberManager;
        this.context = context;
    }

    private ValueHolder getHolder(long uid) {
        return heartbeats.computeIfAbsent(uid, ValueHolder::new);
    }

    @Override
    public void start() {
        memberManager.addListener(new MemberManager.Listener() {
            @Override
            public void onMemberStateChange(Member member, MemberState from, MemberState to) {
                assert context.executor().inExecutor();
                long uid = member.uid();
                if (from.SERVABLE) {
                    if (!to.SERVABLE) {
                        LOG.warn("Member {} not servable", member);
                        servableMemberUidList.remove(uid);
                    }
                } else if (to.SERVABLE) {
                    LOG.info("Member {} become servable", member);
                    servableMemberUidList.add(uid);
                }
                var holder = getHolder(uid);
                holder.member = member;
                holder.status = to;
            }

            @Override
            public void onMemberUpdate(Member from, Member to) {
                var holder = getHolder(to.uid());
                holder.member = to;
            }
        }, false);
        var executor = context.executor();
        this.scheduleList.add(executor.scheduleWithFixedDelay(this::scheduleCheck,
                checkIntervalMillis, checkIntervalMillis, TimeUnit.MILLISECONDS));
        this.scheduleList.add(executor.scheduleWithFixedDelay(this::schedulePing,
                pingIntervalMillis, pingIntervalMillis, TimeUnit.MILLISECONDS));
    }

    @Override
    public void heartbeatFrom(long uid) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("heartbeat from {}", uid);
        }
        getHolder(uid).reset();
    }

    @Override
    public void stop() {
        scheduleList.forEach(future -> future.cancel(true));
        scheduleList.clear();
        heartbeats.values().forEach(ValueHolder::reset);
        heartbeats.clear();
    }

    private void scheduleCheck() {
        long current = System.currentTimeMillis();
        List<MemberEvent> memberEvents = null;
        for (ValueHolder holder : heartbeats.values()) {
            if (holder.status == MemberState.SUSPICIOUS) {
                if (holder.latestTs + memberDownTimeoutMillis < current) {
                    if (memberEvents == null) {
                        memberEvents = new ArrayList<>();
                    }
                    var clock = memberManager.getClock(holder.uid)
                            .merge(memberManager.getClock());
                    memberEvents.add(new MemberEvent(holder.member, MemberAction.STRONG_SUSPECT, clock));
                }
            } else if (holder.status == MemberState.DOWN) {
                if (holder.latestTs + memberFailTimeoutMillis < current) {
                    if (memberEvents == null) {
                        memberEvents = new ArrayList<>();
                    }
                    var clock = memberManager.getClock(holder.uid)
                            .merge(memberManager.getClock());
                    memberEvents.add(new MemberEvent(holder.member, MemberAction.FAIL, clock));
                }
            } else if (memberRemoveTimeoutMillis > 0 &&
                    (holder.status == MemberState.LEFT) &&
                    holder.latestTs + memberRemoveTimeoutMillis < current) {
                if (memberEvents == null) {
                    memberEvents = new ArrayList<>();
                }
                var clock = memberManager.getClock(holder.uid)
                        .merge(memberManager.getClock());
                memberEvents.add(new MemberEvent(holder.member, MemberAction.REMOVE, clock));
            }
        }
        if (memberEvents != null) {
            memberManager.gossipEvent(Gossip.of(memberEvents, memberManager.getSelfUid()));
        }
    }

    private void schedulePing() {
        assert context.executor().inExecutor();
        if (servableMemberUidList.isEmpty()) {
            return;
        }
        if (pingOff >= servableMemberUidList.size()) {
            pingOff = 0;
            Collections.shuffle(servableMemberUidList);
        }

        long pingUid = pingOff == 0 ?
                servableMemberUidList.getLast() :
                servableMemberUidList.get(pingOff - 1);
        var pingClock = memberManager.getClock(pingUid);
        assert pingClock != null;
        long uid = servableMemberUidList.get(pingOff++);
        var member = memberManager.getMember(uid);
        assert member != null;
        var holder = getHolder(uid);
        var memberClock = new MemberClock(pingUid, pingClock);
        Gossip ping = Gossip.ping(memberManager.getSelfUid(), memberClock);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Ping to {}: {}", member, ping);
        }
        member.actor().tell(ping, context.self());
        if (holder.status == MemberState.UP) {
            if (holder.suspectedFuture != null) {
                LOG.warn("Unexpected suspected future for uid: {}", uid);
                holder.suspectedFuture.cancel(false);
            }
            holder.suspectedFuture = context.executor().schedule(() -> {
                if (holder.status != MemberState.UP) {
                    return;
                }
                Member get = memberManager.getMember(holder.uid);
                if (get == null) {
                    return;
                }
                LOG.warn("Member {} ping timeout", get);
                var clock = memberManager.getClock(holder.uid)
                        .merge(memberManager.getClock());
                var event = new MemberEvent(get, MemberAction.SUSPECT, clock);
                memberManager.gossipEvent(Gossip.of(event, memberManager.getSelfUid()));
                holder.suspectedFuture = null;
            }, memberPingTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private static class ValueHolder {
        private final long uid;
        private Member member;
        private MemberState status;
        private long latestTs;
        private ScheduledFuture<?> suspectedFuture;

        private ValueHolder(long uid) {
            this.uid = uid;
        }

        public void reset() {
            latestTs = System.currentTimeMillis();
            if (suspectedFuture != null) {
                suspectedFuture.cancel(false);
                suspectedFuture = null;
            }
        }
    }
}
