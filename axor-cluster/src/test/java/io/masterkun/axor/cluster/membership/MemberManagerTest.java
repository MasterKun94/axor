package io.masterkun.axor.cluster.membership;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.cluster.MemberState;
import io.masterkun.axor.cluster.config.MemberManageConfig;
import io.masterkun.axor.commons.collection.LongObjectHashMap;
import io.masterkun.axor.commons.collection.LongObjectMap;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.testkit.MessageBufferActorRef;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MemberManagerTest {

    private static final List<Throwable> failures = new ArrayList<>();
    private static final Queue<MemberStateChange> memberStateChanges = new LinkedList<>();
    private static final Queue<Member> memberChanges = new LinkedList<>();
    private static final LongObjectMap<Member> members = new LongObjectHashMap<>();
    private static final LongObjectMap<VectorClock> clocks = new LongObjectHashMap<>();
    private static final MemberManager.Listener listener = new MemberManager.Listener() {

        @Override
        public void onMemberStateChange(Member member, MemberState from, MemberState to) {
            memberStateChanges.add(new MemberStateChange(member, from, to));
        }

        @Override
        public void onMemberUpdate(Member from, Member to) {
            memberChanges.add(to);
        }
    };
    private static long selfId;
    private static MemberManager manager;
    private static MemberIdGenerator idGenerator;

    @BeforeClass
    public static void setUpBeforeClass() {
        idGenerator = new MemberIdGenerator(0, 0);
        MemberManageConfig config = new MemberManageConfig(0.8, 10);
        manager = new MemberManager(selfId = idGenerator.nextId(), ActorRef.noSender(), config,
                failures::add);
    }

    public Member getOrCreateMember(long uid, MetaKey.Action... actions) {
        return members.compute(uid, (k, v) -> {
            if (v == null) {
                var path = ActorAddress.create("test", "localhost", 123, "/test/" + k);
                return new Member(uid, MetaInfo.EMPTY.transform(actions),
                        new MessageBufferActorRef<>(path, MsgType.of(Gossip.class)));
            } else {
                return new Member(uid, v.metaInfo().transform(actions), v.actor());
            }
        });
    }

    @Test
    public void test() {
//        MetaKey<Integer> opt = MetaKey.builder(1)
//                .name("TEST")
//                .description("TEST")
//                .build(1);
//
//        // self member0 up
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(selfId),
//                MemberAction.JOIN,
//                manager.internalIncAndGetClock(),
//                selfId
//        ));
//        assertEquals(1, manager.getMembers(MemberState.UP).count());
//
//        // member1 up
//        long uid1 = idGenerator.nextId();
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid1),
//                MemberAction.UPDATE,
//                Unsafe.wrapNoCheck(0, 10),
//                uid1
//        ));
//        checkEvent(uid1,
//                new MemberEvent(getOrCreateMember(selfId), MemberAction.UPDATE));
//        assertEquals(2, manager.getMembers(MemberState.UP).count());
//
//        manager.addListener(listener, false);
//        assertEquals(2, memberStateChanges.size());
//        var events = Arrays.asList(
//                new MemberStateChange(getOrCreateMember(selfId), MemberState.NONE, MemberState
//                .UP),
//                new MemberStateChange(getOrCreateMember(uid1), MemberState.NONE, MemberState.UP)
//        );
//        assertTrue(events.contains(memberStateChanges.poll()));
//        assertTrue(events.contains(memberStateChanges.poll()));
//
//        // member2 up
//        long uid2 = idGenerator.nextId();
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid2),
//                MemberAction.UPDATE,
//                Unsafe.wrapNoCheck(uid2, 10),
//                uid2
//        ));
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        checkEvent(uid2,
//                new MemberEvent(getOrCreateMember(selfId), MemberAction.UPDATE),
//                new MemberEvent(getOrCreateMember(uid1), MemberAction.UPDATE));
//        checkEvent(uid1,
//                new MemberEvent(getOrCreateMember(uid2), MemberAction.UPDATE));
//        assertEquals(new MemberStateChange(getOrCreateMember(uid2), MemberState.NONE,
//        MemberState.UP), memberStateChanges.poll());
//
//        // self member0 update
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(selfId, opt.upsert(111)),
//                MemberAction.UPDATE,
//                manager.incAndGetClock(),
//                selfId
//        ));
//        checkEvent(uid1,
//                new MemberEvent(getOrCreateMember(selfId), MemberAction.UPDATE));
//        checkEvent(uid2,
//                new MemberEvent(getOrCreateMember(selfId), MemberAction.UPDATE));
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        assertEquals(111, members.get(selfId).metaInfo().get(opt).intValue());
//        assertEquals(getOrCreateMember(selfId), memberChanges.poll());
//
//        // member1 suspect self member0
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(selfId),
//                MemberAction.SUSPECT,
//                manager.getClock().inc(uid1),
//                uid1
//        ));
//        checkEvent(uid1, new MemberEvent(getOrCreateMember(selfId), MemberAction.UPDATE));
//        checkEvent(uid2);
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        assertNull(memberStateChanges.poll());
//
//        // member1 update
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid1, opt.upsert(222)),
//                MemberAction.UPDATE,
//                manager.getClock().inc(uid1),
//                uid1
//        ));
//        checkEvent(uid2, new MemberEvent(getOrCreateMember(uid1), MemberAction.UPDATE));
//        checkEvent(uid1);
//        checkEvent(selfId);
//        assertEquals(222, members.get(uid1).metaInfo().get(opt).intValue());
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        assertEquals(getOrCreateMember(uid1), memberChanges.poll());
//
//        // member1 update nochange
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid1, opt.upsert(222)),
//                MemberAction.UPDATE,
//                manager.getClock().inc(uid1),
//                uid1
//        ));
//        checkEvent(uid1);
//        checkEvent(selfId);
//        checkEvent(uid2);
//        assertEquals(222, members.get(uid1).metaInfo().get(opt).intValue());
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        assertNull(memberChanges.poll());
//
//        // member3 up
//        long uid3 = idGenerator.nextId();
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid3),
//                MemberAction.JOIN,
//                Unsafe.wrapNoCheck(uid3, 10),
//                uid3
//        ));
//        assertEquals(4, manager.getMembers(MemberState.UP).count());
//        checkEvent(uid3,
//                new MemberEvent(getOrCreateMember(selfId), MemberAction.UPDATE),
//                new MemberEvent(getOrCreateMember(uid1), MemberAction.UPDATE),
//                new MemberEvent(getOrCreateMember(uid2), MemberAction.UPDATE));
//        checkEvent(uid1,
//                new MemberEvent(getOrCreateMember(uid3), MemberAction.JOIN));
//        checkEvent(uid2,
//                new MemberEvent(getOrCreateMember(uid3), MemberAction.JOIN));
//        assertEquals(new MemberStateChange(getOrCreateMember(uid3), MemberState.NONE,
//        MemberState.UP), memberStateChanges.poll());
//
//        // member1 suspect member3
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid3),
//                MemberAction.SUSPECT,
//                manager.getClock().inc(uid1),
//                uid1
//        ));
//        checkEvent(uid1);
//        checkEvent(uid2,
//                new MemberEvent(getOrCreateMember(uid3), MemberAction.SUSPECT));
//        checkEvent(uid3);
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        assertEquals(1, manager.getMembers(MemberState.SUSPICIOUS).count());
//        assertEquals(0, manager.getMembers(MemberState.DOWN).count());
//        assertEquals(new MemberStateChange(getOrCreateMember(uid3), MemberState.UP, MemberState
//        .SUSPICIOUS), memberStateChanges.poll());
//
//        // member1 strong suspect member3
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid3),
//                MemberAction.STRONG_SUSPECT,
//                manager.getClock().inc(uid1),
//                uid1
//        ));
//        checkEvent(uid1);
//        checkEvent(uid2,
//                new MemberEvent(getOrCreateMember(uid3), MemberAction.STRONG_SUSPECT));
//        checkEvent(uid3);
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        assertEquals(0, manager.getMembers(MemberState.SUSPICIOUS).count());
//        assertEquals(1, manager.getMembers(MemberState.DOWN).count());
//        assertEquals(new MemberStateChange(getOrCreateMember(uid3), MemberState.SUSPICIOUS,
//        MemberState.DOWN), memberStateChanges.poll());
//
//        // member1 leave member3
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid3),
//                MemberAction.LEAVE,
//                manager.getClock().inc(uid1),
//                uid1
//        ));
//        checkEvent(uid1);
//        checkEvent(uid2,
//                new MemberEvent(getOrCreateMember(uid3), MemberAction.LEAVE));
//        checkEvent(uid3,
//                new MemberEvent(getOrCreateMember(uid3), MemberAction.LEAVE_ACK));
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        assertEquals(0, manager.getMembers(MemberState.SUSPICIOUS).count());
//        assertEquals(0, manager.getMembers(MemberState.DOWN).count());
//        assertEquals(1, manager.getMembers(MemberState.LEFT).count());
//        assertEquals(new MemberStateChange(getOrCreateMember(uid3), MemberState.DOWN,
//        MemberState.LEFT), memberStateChanges.poll());
//
//        assertTrue(manager.getClock().get(uid3) > 0);
//
//        // member1 leave member3
//        manager.gossipEvent(new Gossip(
//                getOrCreateMember(uid3),
//                MemberAction.REMOVE,
//                manager.getClock().inc(uid1),
//                uid1
//        ));
//        checkEvent(uid1);
//        checkEvent(uid2,
//                new MemberEvent(getOrCreateMember(uid3), MemberAction.REMOVE));
//        checkEvent(uid3);
//        assertEquals(3, manager.getMembers(MemberState.UP).count());
//        assertEquals(0, manager.getMembers(MemberState.LEFT).count());
//        assertEquals(new MemberStateChange(getOrCreateMember(uid3), MemberState.LEFT,
//        MemberState.REMOVED), memberStateChanges.poll());
//        assertEquals(0, manager.getClock().get(uid3));
//    }
//
//    private void checkEvent(long uid, MemberEvent... events) {
//        Queue<Gossip> queue = ((MessageBufferActorRef<Gossip>) members.get(uid).actor())
//        .getMessageQueue();
//        if (events.length > 0) {
//            assertEquals(1, queue.size());
//        } else {
//            assertEquals(0, queue.size());
//            return;
//        }
//        var poll = queue.poll();
//        assertEquals(selfId, poll.sender());
//        assertEquals(events.length, poll.events().size());
//
//        for (MemberEvent event : events) {
//            var opt = poll.events().stream().filter(e -> e.equals(event)).findFirst();
//            assertTrue(opt.isPresent());
//        }
//        VectorClock clock = poll.clock();
//        clocks.compute(uid, (k, v) -> {
//            if (v != null) {
//                assertTrue(v.isEarlierThan(clock));
//            }
//            return clock;
//        });
    }

    public record MemberStateChange(Member member, MemberState from, MemberState to) {
    }
}
