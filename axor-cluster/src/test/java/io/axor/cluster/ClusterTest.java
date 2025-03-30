package io.axor.cluster;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.ActorSystem;
import io.axor.testkit.actor.ActorTestKit;
import io.axor.testkit.actor.MockActorRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;

public class ClusterTest {
    private static final ActorTestKit testkit = new ActorTestKit(Duration.ofSeconds(1));
    private static ActorSystem system1;
    private static ActorSystem system2;
    private static ActorSystem system3;
    private static MockActorRef<ClusterEvent> listener1;
    private static MockActorRef<ClusterEvent> listener2;
    private static MockActorRef<ClusterEvent> listener3;


    @BeforeClass
    public static void startNode() {
        system1 = createActorSystem("test", 21001);
        system2 = createActorSystem("test", 21002);
        system3 = createActorSystem("test", 21003);
        Cluster cluster1 = Cluster.get("ClusterTest", system1);
        listener1 = testkit.mock(system1.address("listener"), ClusterEvent.class);
        cluster1.addListener(listener1);
        Cluster cluster2 = Cluster.get("ClusterTest", system2);
        listener2 = testkit.mock(system2.address("listener"), ClusterEvent.class);
        cluster2.addListener(listener2);
        Cluster cluster3 = Cluster.get("ClusterTest", system3);
        listener3 = testkit.mock(system3.address("listener"), ClusterEvent.class);
        cluster3.addListener(listener3);
    }


    @AfterClass
    public static void leaveNode() {
        system1.shutdownAsync().join();
        system2.shutdownAsync().join();
        system3.shutdownAsync().join();
    }

    private static ActorSystem createActorSystem(String name, int port) {
        Config config =
                ConfigFactory.load(ConfigFactory.parseString("axor.network.bind.port = " + port))
                        .resolve();
        return ActorSystem.create(name, config);
    }

    @Test
    public void test() throws Exception {
        ClusterEvent event;
        while ((event = listener1.pollMessage()) != null) {
            System.out.println(event);
        }
        System.out.println("---");
        while ((event = listener2.pollMessage()) != null) {
            System.out.println(event);
        }
        System.out.println("---");
        while ((event = listener3.pollMessage()) != null) {
            System.out.println(event);
        }
    }
}
