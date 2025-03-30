package io.masterkun.axor.example;

import com.typesafe.config.Config;
import io.masterkun.axor.api.Actor;
import io.masterkun.axor.api.ActorContext;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.cluster.Cluster;
import io.masterkun.axor.cluster.singleton.SingletonSystem;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.serde.kryo.KryoSerdeFactory;
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

    private static ActorSystem startSystem(int port) {
        Config config = load(parseString(("""
                axor.network.bind {
                    port = %d
                    host = "localhost"
                }
                axor.cluster.default-cluster {
                    join.seeds = ["localhost:1101"]
                }
                """.formatted(port)))).resolve();
        return ActorSystem.create("example", config);
    }


    public static class Node1 {
        public static void main(String[] args) {
            ActorSystem system = startSystem(1101);
            system.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
                    .addInitializer(kryo -> {
                        kryo.register(Hello.class, 601);
                        kryo.register(HelloBack.class, 602);
                    });
            Cluster cluster = Cluster.get(system);
            SingletonSystem singletonSystem = SingletonSystem.get(cluster);
            ActorRef<Hello> singletonProxy = singletonSystem.getOrStart(HelloWorldActor::new,
                    MsgType.of(Hello.class), "HelloSingleton");
            ActorRef<HelloBack> bot = system.start(HelloBot::new, "HelloBot");
            system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
                singletonProxy.tell(new Hello("Hello"), bot);
            }, 3, 3, TimeUnit.SECONDS);
        }
    }


    public static class Node2 {
        public static void main(String[] args) {
            ActorSystem system = startSystem(1102);
            system.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
                    .addInitializer(kryo -> {
                        kryo.register(Hello.class, 601);
                        kryo.register(HelloBack.class, 602);
                    });
            Cluster cluster = Cluster.get(system);
            SingletonSystem singletonSystem = SingletonSystem.get(cluster);
            ActorRef<Hello> singletonProxy = singletonSystem.getOrStart(HelloWorldActor::new,
                    MsgType.of(Hello.class), "HelloSingleton");
            ActorRef<HelloBack> bot = system.start(HelloBot::new, "HelloBot");
            system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
                singletonProxy.tell(new Hello("Hello"), bot);
            }, 3, 3, TimeUnit.SECONDS);
        }
    }

    public static class Node3 {
        public static void main(String[] args) {
            ActorSystem system = startSystem(1103);
            system.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
                    .addInitializer(kryo -> {
                        kryo.register(Hello.class, 601);
                        kryo.register(HelloBack.class, 602);
                    });
            Cluster cluster = Cluster.get(system);
            SingletonSystem singletonSystem = SingletonSystem.get(cluster);
            ActorRef<Hello> singletonProxy = singletonSystem.getOrStart(HelloWorldActor::new,
                    MsgType.of(Hello.class), "HelloSingleton");
            ActorRef<HelloBack> bot = system.start(HelloBot::new, "HelloBot");
            system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
                singletonProxy.tell(new Hello("Hello"), bot);
            }, 3, 3, TimeUnit.SECONDS);
        }
    }

    public record Hello(String msg) {
    }

    public record HelloBack(String msg) {
    }

    public static class HelloWorldActor extends Actor<Hello> {
        private static final Logger LOG = LoggerFactory.getLogger(HelloWorldActor.class);

        protected HelloWorldActor(ActorContext<Hello> context) {
            super(context);
        }

        @Override
        public void onReceive(Hello sayHello) {
            LOG.info("Receive: {} from {}", sayHello, sender());
            sender(HelloBack.class).tell(new HelloBack(sayHello.msg), self());
        }

        @Override
        public MsgType<Hello> msgType() {
            return MsgType.of(Hello.class);
        }
    }

    public static class HelloBot extends Actor<HelloBack> {
        private static final Logger LOG = LoggerFactory.getLogger(HelloBot.class);

        protected HelloBot(ActorContext<HelloBack> context) {
            super(context);
        }

        @Override
        public void onReceive(HelloBack helloBack) {
            LOG.info("Receive: {} from {}", helloBack, sender());
        }

        @Override
        public MsgType<HelloBack> msgType() {
            return MsgType.of(HelloBack.class);
        }
    }
}
