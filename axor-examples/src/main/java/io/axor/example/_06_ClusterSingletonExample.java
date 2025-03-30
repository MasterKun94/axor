package io.axor.example;

import com.typesafe.config.Config;
import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.cluster.Cluster;
import io.axor.cluster.singleton.SingletonSystem;
import io.axor.runtime.MsgType;
import io.axor.runtime.serde.kryo.KryoSerdeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;

/**
 * This class demonstrates how to create a cluster singleton actor in an Axon system. It sets up a
 * cluster with multiple nodes, each attempting to start a singleton actor. The first node to
 * successfully start the singleton will be the one that runs it, and the others will proxy their
 * messages to this instance. Each node also starts a bot actor that periodically sends messages to
 * the singleton actor.
 */
public class _06_ClusterSingletonExample {

    private static void startNode(int port) {
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
                    kryo.register(Hello.class, 601);
                    kryo.register(HelloReply.class, 602);
                });
        Cluster cluster = Cluster.get(system);
        SingletonSystem singletonSystem = SingletonSystem.get(cluster);
        ActorRef<Hello> singletonProxy = singletonSystem.getOrStart(HelloWorldActor::new,
                MsgType.of(Hello.class), "HelloSingleton");
        ActorRef<HelloReply> bot = system.start(HelloBot::new, "HelloBot");
        system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
            singletonProxy.tell(new Hello("Hello"), bot);
        }, 3, 3, TimeUnit.SECONDS);
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

    public record Hello(String msg) {
    }

    public record HelloReply(String msg) {
    }

    public static class HelloWorldActor extends Actor<Hello> {
        private static final Logger LOG = LoggerFactory.getLogger(HelloWorldActor.class);

        protected HelloWorldActor(ActorContext<Hello> context) {
            super(context);
        }

        @Override
        public void onReceive(Hello sayHello) {
            LOG.info("Receive: {} from {}", sayHello, sender());
            sender(HelloReply.class).tell(new HelloReply(sayHello.msg), self());
        }

        @Override
        public MsgType<Hello> msgType() {
            return MsgType.of(Hello.class);
        }
    }

    public static class HelloBot extends Actor<HelloReply> {
        private static final Logger LOG = LoggerFactory.getLogger(HelloBot.class);

        protected HelloBot(ActorContext<HelloReply> context) {
            super(context);
        }

        @Override
        public void onReceive(HelloReply HelloReply) {
            LOG.info("Receive: {} from {}", HelloReply, sender());
        }

        @Override
        public MsgType<HelloReply> msgType() {
            return MsgType.of(HelloReply.class);
        }
    }
}
