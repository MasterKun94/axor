package io.axor.cluster;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorSystem;
import io.axor.runtime.MsgType;
import io.axor.testkit.actor.ActorTestKit;
import io.axor.testkit.actor.MockActorRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;

import static io.axor.testkit.actor.MsgAssertions.eq;

public class ClusterPubsubTest {
    private static final ActorTestKit testkit = new ActorTestKit(Duration.ofSeconds(1));
    private static ActorSystem system1;
    private static ActorSystem system2;
    private static ActorSystem system3;
    private static Cluster cluster1;
    private static Cluster cluster2;
    private static Cluster cluster3;
    private static MockActorRef<String> subscriber1;
    private static MockActorRef<String> subscriber2;
    private static MockActorRef<String> subscriber3;
    private static MockActorRef<String> someActor;

    private static ActorSystem createActorSystem(String name, int port) {
        Config config =
                ConfigFactory.load(ConfigFactory.parseString("axor.network.bind.port = " + port))
                        .resolve();
        return ActorSystem.create(name, config);
    }

    @BeforeClass
    public static void startNode() {
        system1 = createActorSystem("test", 11001);
        system2 = createActorSystem("test", 11002);
        system3 = createActorSystem("test", 11003);
        cluster1 = Cluster.get("ClusterTest", system1);
        cluster2 = Cluster.get("ClusterTest", system2);
        cluster3 = Cluster.get("ClusterTest", system3);
        subscriber1 = testkit.mock("subscriber", String.class, system1);
        subscriber2 = testkit.mock("subscriber", String.class, system2);
        subscriber3 = testkit.mock("subscriber", String.class, system3);
        someActor = testkit.mock("someActor", String.class, system1);
    }

    @AfterClass
    public static void leaveNode() {
        system1.shutdownAsync().join();
        system2.shutdownAsync().join();
        system3.shutdownAsync().join();
    }

    @Test
    public void testPubsub() throws Exception {
        cluster1.pubsub("testPubsub", MsgType.of(String.class)).subscribe(subscriber1);
        cluster2.pubsub("testPubsub", MsgType.of(String.class)).subscribe(subscriber2);
        cluster3.pubsub("testPubsub", MsgType.of(String.class)).subscribe(subscriber3);
        Thread.sleep(500);
        cluster3.pubsub("testPubsub", MsgType.of(String.class))
                .publishToAll("Hello", someActor);
        subscriber1.expectReceive(eq("Hello", someActor.address()));
        subscriber2.expectReceive(eq("Hello", someActor.address()));
        subscriber3.expectReceive(eq("Hello", someActor.address()));

        cluster1.pubsub("testPubsub", MsgType.of(String.class)).unsubscribe(subscriber1);
        Thread.sleep(500);
        cluster3.pubsub("testPubsub", MsgType.of(String.class))
                .publishToAll("World", someActor);
        subscriber1.expectNoMsg();
        subscriber2.expectReceive(eq("World", someActor.address()));
        subscriber3.expectReceive(eq("World", someActor.address()));

        cluster1.pubsub("testPubsub", MsgType.of(String.class)).subscribe(subscriber1);
        cluster2.pubsub("testPubsub", MsgType.of(String.class)).unsubscribe(subscriber2);
        cluster2.pubsub("testPubsub", MsgType.of(String.class)).unsubscribe(subscriber3);
        Thread.sleep(500);
        cluster3.pubsub("testPubsub", MsgType.of(String.class))
                .publishToAll("!!!", someActor);
        subscriber1.expectReceive(eq("!!!", someActor.address()));
        subscriber2.expectNoMsg();
        subscriber3.expectReceive(eq("!!!", someActor.address()));

        cluster3.pubsub("testPubsub", MsgType.of(String.class))
                .sendToOne("aaa", someActor);
        cluster3.pubsub("testPubsub", MsgType.of(String.class))
                .sendToOne("aaa", someActor);
        subscriber1.expectReceive(eq("aaa", someActor.address()));
        subscriber3.expectReceive(eq("aaa", someActor.address()));
        subscriber1.expectNoMsg();
        subscriber2.expectNoMsg();
        subscriber3.expectNoMsg();
    }

    private static final class NoopActor extends Actor<String> {

        NoopActor(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onReceive(String s) {

        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }
    }
}
