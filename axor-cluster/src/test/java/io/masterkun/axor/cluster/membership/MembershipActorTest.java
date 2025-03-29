package io.masterkun.axor.cluster.membership;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.masterkun.axor.api.Actor;
import io.masterkun.axor.api.ActorContext;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.Pubsub;
import io.masterkun.axor.cluster.Cluster;
import io.masterkun.axor.cluster.ClusterEvent;
import io.masterkun.axor.cluster.singleton.SingletonSystem;
import io.masterkun.axor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MembershipActorTest {

    private static ActorSystem createActorSystem(String name, int port) {
        Config config =
                ConfigFactory.load(ConfigFactory.parseString("axor.network.bind.port = " + port))
                        .resolve();
        return ActorSystem.create(name, config);
    }

    private static void start(int port, Logger log) {
        ActorSystem system = createActorSystem("test", port);
        Cluster cluster = Cluster.get("MembershipActorTest", system);
        cluster.addListener(system.start(ClusterEventListener::new, "cluster_listener"));
        system.start(ClusterSubscriber::new, "ClusterSubscriber");
        SingletonSystem singletonSystem = SingletonSystem.get(cluster);
        ActorRef<String> proxy = singletonSystem.getOrStart(ClusterPublisher::new,
                MsgType.of(String.class), "ClusterPublisher");
        system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
            proxy.tell("Test");
        }, 5, 5, TimeUnit.SECONDS);
    }

    public static class Node1 {
        public static void main(String[] args) {
            start(10001, LoggerFactory.getLogger(Node1.class));
        }
    }


    public static class Node2 {
        public static void main(String[] args) {
            start(10002, LoggerFactory.getLogger(Node2.class));
        }
    }


    public static class Node3 {
        public static void main(String[] args) {
            start(10003, LoggerFactory.getLogger(Node3.class));
        }
    }

    private static class ClusterEventListener extends Actor<ClusterEvent> {
        private static final Logger log = LoggerFactory.getLogger(ClusterEventListener.class);

        protected ClusterEventListener(ActorContext<ClusterEvent> context) {
            super(context);
        }

        @Override
        public void onReceive(ClusterEvent clusterEvent) {
            log.debug("Receive event: [{}]", clusterEvent);
        }

        @Override
        public MsgType<ClusterEvent> msgType() {
            return MsgType.of(ClusterEvent.class);
        }
    }

    private static class ClusterPublisher extends Actor<String> {
        private static final Logger log = LoggerFactory.getLogger(ClusterPublisher.class);
        private Pubsub<String> pubsub;
        private ScheduledFuture<?> s;

        protected ClusterPublisher(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onStart() {
            pubsub = Cluster.get("MembershipActorTest", context().system())
                    .pubsub("SingletonPubsub", MsgType.of(String.class));
            s = context().dispatcher().scheduleAtFixedRate(() -> {
                pubsub.publishToAll("Hello", self());
            }, 1, 1, TimeUnit.SECONDS);
        }

        @Override
        public void onReceive(String s) {
            log.info("Receive {} from [{}]", s, sender());
        }

        @Override
        public void preStop() {
            s.cancel(true);
        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }
    }

    private static class ClusterSubscriber extends Actor<String> {
        private static final Logger log = LoggerFactory.getLogger(ClusterSubscriber.class);

        protected ClusterSubscriber(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onStart() {
            Cluster.get("MembershipActorTest", context().system())
                    .pubsub("SingletonPubsub", MsgType.of(String.class))
                    .subscribe(self());
        }

        @Override
        public void onReceive(String s) {
            log.info("Receive {} from [{}]", s, sender());
            sender(MsgType.of(String.class)).tell("Hi", sender());
        }

        @Override
        public void preStop() {
            Cluster.get("MembershipActorTest", context().system())
                    .pubsub("SingletonPubsub", MsgType.of(String.class))
                    .unsubscribe(self());
        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }
    }
}
