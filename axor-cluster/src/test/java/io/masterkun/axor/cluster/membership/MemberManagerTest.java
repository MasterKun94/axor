package io.masterkun.axor.cluster.membership;

import com.typesafe.config.ConfigFactory;
import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.cluster.MemberState;
import io.masterkun.axor.cluster.config.MemberManageConfig;
import io.masterkun.axor.commons.config.ConfigMapper;
import io.masterkun.axor.testkit.actor.ActorTestKit;
import io.masterkun.axor.testkit.actor.MockActorRef;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static io.masterkun.axor.testkit.actor.MsgAssertions.eq;

public class MemberManagerTest {
    private static final ActorTestKit testKit = new ActorTestKit(Duration.ofMillis(10));
    private static final MockActorRef<Gossip> node1 = testKit.mock(
            ActorAddress.create("test@localhost:123/node1"),
            Gossip.class
    );
    private static Member member1 = new Member(1, MetaInfo.EMPTY, node1);
    private static final MockActorRef<Gossip> node2 = testKit.mock(
            ActorAddress.create("test@localhost:123/node2"),
            Gossip.class
    );
    private static Member member2 = new Member(2, MetaInfo.EMPTY, node2);
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
    private static final Consumer<Throwable> failureHook = e -> {throw new RuntimeException(e);};
    private static final MemberManageConfig config = ConfigMapper.map(ConfigFactory.parseString("""
            publishRate=0.8
            publishNumMin=5
            """), MemberManageConfig.class);
    private static final MemberManager memberManager = new MemberManager(1, node1, config, failureHook);
    private static final MockActorRef<ListenerEvent> listener = testKit.mock(
            ActorAddress.create("test@localhost:123/listener"),
            ListenerEvent.class
    );
    private static final MetaKey<String> testKey = MetaKeys.create(123, "test", "Test", "default_value");

    @BeforeClass
    public static void setup() throws Exception {
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
    public void testAll() throws Exception {
        _00_join_node1();
        _01_join_node2();
        _02_join_node3();
        _03_update_node1();
        _04_update_node2_nochange();
    }

    private void _00_join_node1() throws Exception {
        memberManager.gossipEvent(Gossip.of(new MemberEvent(
                member1, MemberAction.JOIN, VectorClock.wrap(0, 1)
        ), 1));
        node1.expectNoMsg();
        listener.expectReceive(eq(new MemberStateChange(member1, MemberState.NONE, MemberState.UP)));
        listener.expectNoMsg();
    }

    private void _01_join_node2() throws Exception {
        memberManager.gossipEvent(Gossip.of(new MemberEvent(
                member2, MemberAction.JOIN, VectorClock.wrap(0, 2)
        ), 2));
        node2.expectNoMsg();
        node1.expectNoMsg();
        listener.expectReceive(eq(new MemberStateChange(member2, MemberState.NONE, MemberState.UP)));
        listener.expectNoMsg();
    }

    private void _02_join_node3() throws Exception {
        MemberEvent event = new MemberEvent(
                member3, MemberAction.JOIN, VectorClock.wrap(0, 1)
        );
        memberManager.gossipEvent(Gossip.of(event, 3));
        node2.expectReceive(eq(Gossip.of(event, 1)));
        node1.expectNoMsg();
        listener.expectReceive(eq(new MemberStateChange(member3, MemberState.NONE, MemberState.UP)));
        listener.expectNoMsg();
    }

    private void _03_update_node1() throws Exception {
        var prev = member1;
        member1 = member1.metaTransform(testKey.update(s -> "new_value"));
        MemberEvent event = new MemberEvent(
                member1, MemberAction.UPDATE, VectorClock.wrap(0, 2)
        );
        memberManager.gossipEvent(Gossip.of(event, 1));
        node1.expectNoMsg();
        node2.expectReceive(eq(Gossip.of(event, 1)));
        node3.expectReceive(eq(Gossip.of(event, 1)));
        listener.expectReceive(eq(new MemberUpdate(prev, member1)));
        listener.expectNoMsg();
    }

    private void _04_update_node2_nochange() throws Exception {
        var newMember = member2.metaTransform(testKey.update(s -> "new_value"));
        MemberEvent event = new MemberEvent(
                newMember, MemberAction.UPDATE, VectorClock.wrap(0, 1)
        );
        memberManager.gossipEvent(Gossip.of(event, 3));
        node1.expectNoMsg();
        node2.expectNoMsg();
        node3.expectReceive(Gossip.of(new MemberEvent(
                member2, MemberAction.UPDATE, VectorClock.wrap(0, 2)
        ), 1));
        listener.expectNoMsg();
    }

    private void _05_suspect_node2() throws Exception {

    }

    private interface ListenerEvent { }
    private record MemberUpdate(Member from, Member to) implements ListenerEvent {}
    private record MemberStateChange(Member member, MemberState from, MemberState to) implements ListenerEvent {}
}
