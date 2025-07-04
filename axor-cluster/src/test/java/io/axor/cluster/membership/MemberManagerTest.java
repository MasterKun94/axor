package io.axor.cluster.membership;

import com.typesafe.config.ConfigFactory;
import io.axor.api.ActorAddress;
import io.axor.cluster.MemberState;
import io.axor.cluster.config.MemberManageConfig;
import io.axor.commons.config.ConfigMapper;
import io.axor.testkit.actor.ActorTestKit;
import io.axor.testkit.actor.MockActorRef;
import io.axor.testkit.actor.MsgAssertions;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;

public class MemberManagerTest {
    private static final ActorTestKit testKit = new ActorTestKit(Duration.ofMillis(10));
    private static final MockActorRef<Gossip> node1 = testKit.mock(
            ActorAddress.create("test@localhost:123/node1"),
            Gossip.class
    );
    private static Member member1 = new Member(1, MetaInfo.EMPTY, node1);
    private static final MemberManager memberManager = new MemberManager(1, node1,
            ConfigMapper.map(ConfigFactory.parseString("""
                    publishRate=0.8
                    publishNumMin=5
                    """), MemberManageConfig.class),
            e -> {
                throw new RuntimeException(e);
            });
    private static final MockActorRef<Gossip> node2 = testKit.mock(
            ActorAddress.create("test@localhost:123/node2"),
            Gossip.class
    );
    private static final Member member2 = new Member(2, MetaInfo.EMPTY, node2);
    private static final MockActorRef<Gossip> node3 = testKit.mock(
            ActorAddress.create("test@localhost:123/node3"),
            Gossip.class
    );
    private static final Member member3 = new Member(3, MetaInfo.EMPTY, node3);
    private static final MockActorRef<Gossip> node4 = testKit.mock(
            ActorAddress.create("test@localhost:123/node4"),
            Gossip.class
    );
    private static final Member member4 = new Member(4, MetaInfo.EMPTY, node4);
    private static final MockActorRef<Gossip> node5 = testKit.mock(
            ActorAddress.create("test@localhost:123/node5"),
            Gossip.class
    );
    private static final Member member5 = new Member(5, MetaInfo.EMPTY, node5);
    private static final MockActorRef<ListenerEvent> listener = testKit.mock(
            ActorAddress.create("test@localhost:123/listener"),
            ListenerEvent.class
    );
    private static final MetaKey<String> testKey = MetaKeys.create(123, "test", "Test",
            "default_value");

    @BeforeClass
    public static void setup() {
        memberManager.addListener(new MemberManager.Listener() {
            @Override
            public void onMemberUpdate(Member from, Member to) {
                listener.tell(new MemberUpdate(from, to));
            }

            @Override
            public void onMemberStateChange(Member member, MemberState from, MemberState to) {
                listener.tell(new MemberStateChange(member, from, to));
            }
        }, true);
    }

    @Test
    public void testAll() {
        _00_join_node1();
        _01_join_node2();
        _02_join_node3();
        _03_update_node1();
        _04_update_node2_nochange();
        _05_join_node45();
        _06_suspect_node1_nochange();
        _07_suspect_node3_nochange();
        _08_suspect_node3();
        _09_strong_suspect_node3();
        _10_update_node3();
        _11_leave_node3();
        _12_strong_suspect_node5();
        _13_down_node5();
    }

    private void _00_join_node1() {
        memberManager.gossipEvent(Gossip.of(new MemberEvent(
                member1, MemberAction.JOIN, VectorClock.wrap(0, 1)
        ), 1));
        node1.expectNoMsgReceive();
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member1, MemberState.NONE,
                MemberState.UP)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(1, 1), memberManager.getClock());
    }

    private void _01_join_node2() {
        memberManager.gossipEvent(Gossip.of(new MemberEvent(
                member2, MemberAction.JOIN, VectorClock.wrap(0, 2)
        ), 2));
        node2.expectNoMsgReceive();
        node1.expectNoMsgReceive();
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member2, MemberState.NONE,
                MemberState.UP)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 2), memberManager.getClock(2));
    }

    private void _02_join_node3() {
        MemberEvent event = new MemberEvent(
                member3, MemberAction.JOIN, VectorClock.wrap(0, 2)
        );
        memberManager.gossipEvent(Gossip.of(event, 3));
        node2.expectReceive(MsgAssertions.msgEq(Gossip.of(event, 1)));
        node1.expectNoMsgReceive();
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member3, MemberState.NONE,
                MemberState.UP)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 2), memberManager.getClock(3));
    }

    private void _03_update_node1() {
        var prev = member1;
        member1 = member1.metaTransform(testKey.update(s -> "new_value"));
        MemberEvent event = new MemberEvent(
                member1, MemberAction.UPDATE, VectorClock.wrap(0, 2)
        );
        memberManager.gossipEvent(Gossip.of(event, 1));
        node1.expectNoMsgReceive();
        node2.expectReceive(MsgAssertions.msgEq(Gossip.of(event, 1)));
        node3.expectReceive(MsgAssertions.msgEq(Gossip.of(event, 1)));
        listener.expectReceive(MsgAssertions.msgEq(new MemberUpdate(prev, member1)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 2), memberManager.getClock(1));
    }

    private void _04_update_node2_nochange() {
        var newMember = member2.metaTransform(testKey.update(s -> "new_value"));
        MemberEvent event = new MemberEvent(
                newMember, MemberAction.UPDATE, VectorClock.wrap(0, 1)
        );
        memberManager.gossipEvent(Gossip.of(event, 3));
        node1.expectNoMsgReceive();
        node2.expectNoMsgReceive();
        node3.expectReceive(Gossip.of(new MemberEvent(
                member2, MemberAction.UPDATE, VectorClock.wrap(0, 2)
        ), 1));
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 2), memberManager.getClock(2));
    }

    private void _05_join_node45() {
        MemberEvents events = new MemberEvents(
                new MemberEvent(member4, MemberAction.JOIN, VectorClock.wrap(0, 1)),
                new MemberEvent(member5, MemberAction.JOIN, VectorClock.wrap(0, 2))
        );
        memberManager.gossipEvent(Gossip.of(events, 3));
        node1.expectNoMsgReceive();
        node2.expectReceive(Gossip.of(events, 1));
        node3.expectNoMsgReceive();
        node4.expectReceive(Gossip.of(events, 1));
        node5.expectReceive(Gossip.of(events, 1));
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member4, MemberState.NONE,
                MemberState.UP)));
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member5, MemberState.NONE,
                MemberState.UP)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 1), memberManager.getClock(4));
        Assert.assertEquals(VectorClock.wrap(0, 2), memberManager.getClock(5));
    }

    private void _06_suspect_node1_nochange() {
        MemberEvent event = new MemberEvent(
                member1, MemberAction.SUSPECT, VectorClock.wrap(0, 1, 2, 3)
        );
        memberManager.gossipEvent(Gossip.of(event, 2));
        node1.expectNoMsgReceive();
        node2.expectReceive(Gossip.of(new MemberEvent(
                member1, MemberAction.UPDATE, VectorClock.wrap(0, 2)
        ), 1));
        node3.expectNoMsgReceive();
        node4.expectNoMsgReceive();
        node5.expectNoMsgReceive();
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 2), memberManager.getClock(1));
    }

    private void _07_suspect_node3_nochange() {
        MemberEvent event = new MemberEvent(
                member3, MemberAction.SUSPECT, VectorClock.wrap(0, 1, 2, 3)
        );
        memberManager.gossipEvent(Gossip.of(event, 2));
        node1.expectNoMsgReceive();
        node2.expectReceive(Gossip.of(new MemberEvent(
                member3, MemberAction.UPDATE, VectorClock.wrap(0, 2)
        ), 1));
        node3.expectNoMsgReceive();
        node4.expectNoMsgReceive();
        node5.expectNoMsgReceive();
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 2), memberManager.getClock(3));
    }

    private void _08_suspect_node3() {
        MemberEvent event = new MemberEvent(
                member3, MemberAction.SUSPECT, VectorClock.wrap(0, 3, 2, 4)
        );
        memberManager.gossipEvent(Gossip.of(event, 2));
        node1.expectNoMsgReceive();
        node2.expectNoMsgReceive();
        node3.expectNoMsgReceive();
        node4.expectReceive(Gossip.of(event, 1));
        node5.expectReceive(Gossip.of(event, 1));
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member3, MemberState.UP,
                MemberState.SUSPICIOUS)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 3, 2, 4), memberManager.getClock(3));
    }

    private void _09_strong_suspect_node3() {
        MemberEvent event = new MemberEvent(
                member3, MemberAction.STRONG_SUSPECT, VectorClock.wrap(0, 3, 2, 5)
        );
        memberManager.gossipEvent(Gossip.of(event, 2));
        node1.expectNoMsgReceive();
        node2.expectNoMsgReceive();
        node3.expectNoMsgReceive();
        node4.expectReceive(Gossip.of(event, 1));
        node5.expectReceive(Gossip.of(event, 1));
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member3,
                MemberState.SUSPICIOUS,
                MemberState.DOWN)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(VectorClock.wrap(0, 3, 2, 5), memberManager.getClock(3));
    }

    private void _10_update_node3() {
        MemberEvent event = new MemberEvent(
                member3, MemberAction.UPDATE, VectorClock.wrap(0, 4)
        );
        memberManager.gossipEvent(Gossip.of(event, 4));
        node1.expectNoMsgReceive();
        node2.expectReceive(Gossip.of(event, 1));
        node3.expectReceive(Gossip.of(event, 1));
        node4.expectNoMsgReceive();
        node5.expectReceive(Gossip.of(event, 1));
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member3, MemberState.DOWN,
                MemberState.UP)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(event.clock(), memberManager.getClock(3));
    }

    private void _11_leave_node3() {
        MemberEvent event = new MemberEvent(
                member3, MemberAction.LEAVE, VectorClock.wrap(0, 5)
        );
        memberManager.gossipEvent(Gossip.of(event, 4));
        node1.expectNoMsgReceive();
        node2.expectReceive(Gossip.of(event, 1));
        node3.expectReceive(Gossip.of(new MemberEvent(
                member3, MemberAction.LEAVE_ACK, VectorClock.wrap(0, 5)
        ), 1));
        node4.expectNoMsgReceive();
        node5.expectReceive(Gossip.of(event, 1));
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member3, MemberState.UP,
                MemberState.LEFT)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(event.clock(), memberManager.getClock(3));
    }

    private void _12_strong_suspect_node5() {
        MemberEvent event = new MemberEvent(
                member5, MemberAction.STRONG_SUSPECT, VectorClock.wrap(0, 2, 4, 2)
        );
        memberManager.gossipEvent(Gossip.of(event, 4));
        node1.expectNoMsgReceive();
        node2.expectReceive(Gossip.of(event, 1));
        node3.expectNoMsgReceive();
        node4.expectNoMsgReceive();
        node5.expectNoMsgReceive();
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member5, MemberState.UP,
                MemberState.DOWN)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(event.clock(), memberManager.getClock(5));
    }

    private void _13_down_node5() {
        MemberEvent event = new MemberEvent(
                member5, MemberAction.FAIL, VectorClock.wrap(0, 2, 4, 3)
        );
        memberManager.gossipEvent(Gossip.of(event, 4));
        node1.expectNoMsgReceive();
        node2.expectReceive(Gossip.of(event, 1));
        node3.expectNoMsgReceive();
        node4.expectNoMsgReceive();
        node5.expectNoMsgReceive();
        listener.expectReceive(MsgAssertions.msgEq(new MemberStateChange(member5, MemberState.DOWN,
                MemberState.LEFT)));
        listener.expectNoMsgReceive();
        Assert.assertEquals(event.clock(), memberManager.getClock(5));
    }


    private interface ListenerEvent {
    }

    private record MemberUpdate(Member from, Member to) implements ListenerEvent {
    }

    private record MemberStateChange(Member member, MemberState from,
                                     MemberState to) implements ListenerEvent {
    }
}
