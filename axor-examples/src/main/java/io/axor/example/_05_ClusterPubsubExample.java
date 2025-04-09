package io.axor.example;

import com.typesafe.config.Config;
import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorSystem;
import io.axor.api.Pubsub;
import io.axor.cluster.Cluster;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import io.axor.runtime.serde.kryo.KryoSerdeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;

/**
 * This class demonstrates a cluster-based pub/sub example using Axor. It includes the setup of an
 * actor system, definition of message types, and the implementation of publisher and subscriber
 * actors across multiple nodes.
 */
public class _05_ClusterPubsubExample {

    private static void startNode(int port) throws Exception {
        Config config = load(parseString(("""
                axor.network.bind {
                    port = %d
                    host = "localhost"
                }
                axor.cluster.default-cluster {
                    join.seeds = ["localhost:1101"]
                }
                """.formatted(port)))).resolve();
        ActorSystem system = ActorSystem.create("example", config);
        system.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
                .addInitializer(kryo -> {
                    kryo.register(PublishMessage.class, 501);
                    kryo.register(SendToOneMessage.class, 502);
                });
        Pubsub<TopicMessage> pubsub = Cluster.get(system)
                .pubsub("example-topic", MsgType.of(TopicMessage.class));
        pubsub.subscribe(system.start(Subscriber::new, "subscriber"));
        Thread.sleep(1000);
        var publisher = system.start(Publisher::new, "publisher");

        system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(new Runnable() {
            private int id;

            @Override
            public void run() {
                pubsub.sendToOne(new SendToOneMessage(
                        id++,
                        UUID.randomUUID().toString()
                ), publisher);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public sealed interface TopicMessage {
    }

    public record PublishMessage(int id, String content) implements TopicMessage {
    }

    public record SendToOneMessage(int id, String content) implements TopicMessage {
    }


    public static class Node1 {
        public static void main(String[] args) throws Exception {
            startNode(1101);
        }
    }

    public static class Node2 {
        public static void main(String[] args) throws Exception {
            startNode(1102);

        }
    }

    public static class Node3 {
        public static void main(String[] args) throws Exception {
            startNode(1103);
        }
    }

    public static class Subscriber extends Actor<TopicMessage> {
        private static final Logger LOG = LoggerFactory.getLogger(Subscriber.class);

        protected Subscriber(ActorContext<TopicMessage> context) {
            super(context);
        }

        @Override
        public void onReceive(TopicMessage msg) {
            LOG.info("Receive: {} from {}", msg, sender());
        }

        @Override
        public void onSignal(Signal signal) {
            LOG.info("Signal: {}", signal);
        }

        @Override
        public MsgType<TopicMessage> msgType() {
            return MsgType.of(TopicMessage.class);
        }
    }

    public static class Publisher extends Actor<String> {
        private ScheduledFuture<?> future;
        private int id;

        protected Publisher(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onStart() {
            Cluster cluster = Cluster.get(context().system());
            Pubsub<TopicMessage> pubsub = cluster.pubsub("example-topic",
                    MsgType.of(TopicMessage.class));
            future = context().dispatcher().scheduleAtFixedRate(() -> {
                pubsub.publishToAll(new PublishMessage(
                        id++,
                        UUID.randomUUID().toString()
                ), self());
            }, 5, 5, TimeUnit.SECONDS);
        }

        @Override
        public void preStop() {
            future.cancel(false);
        }

        @Override
        public void onReceive(String s) {
            context().deadLetter(s);
        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }
    }
}
