package io.masterkun.axor.example;

import com.typesafe.config.Config;
import io.masterkun.axor.api.Actor;
import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorContext;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.exception.ActorNotFoundException;
import io.masterkun.axor.exception.IllegalMsgTypeException;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.serde.kryo.KryoSerdeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;

/**
 * This class demonstrates a simple example of remote communication between two actor systems. It
 * sets up two nodes, each running in its own ActorSystem. Node1 acts as a server, and Node2 acts as
 * a client. The client periodically sends Ping messages to the server, and the server responds with
 * Pong messages. Communication is facilitated using Kryo for message serialization.
 */
public class _03_RemoteContactExample {

    private static ActorSystem startSystem(int port) {
        Config config = load(parseString(("""
                axor.network.bind {
                    port = %d
                    host = "localhost"
                }
                """.formatted(port)))).resolve();
        return ActorSystem.create("example", config);
    }

    public static class Node1 {
        public static void main(String[] args) {
            ActorSystem system = startSystem(1101);
            system.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
                    .addInitializer(kryo -> {
                        kryo.register(Ping.class, 301);
                        kryo.register(Pong.class, 302);
                    });
            system.start(ServerActor::new, "serverActor");
        }
    }

    public static class Node2 {
        public static void main(String[] args) {
            ActorSystem system = startSystem(1102);
            system.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
                    .addInitializer(kryo -> {
                        kryo.register(Ping.class, 301);
                        kryo.register(Pong.class, 302);
                    });
            ActorAddress address = ActorAddress.create("example@localhost:1101/serverActor");
            system.<Pong>start(ctx -> new ClientActor(ctx, address), "clientActor");
        }
    }

    public record Ping(int id) {
    }

    public record Pong(int id) {
    }

    public static class ClientActor extends Actor<Pong> {
        private static final Logger LOG = LoggerFactory.getLogger(ClientActor.class);
        private final ActorAddress pingAddress;
        private int id;

        protected ClientActor(ActorContext<Pong> context, ActorAddress pingAddress) {
            super(context);
            this.pingAddress = pingAddress;
        }

        @Override
        public void onStart() {
            ActorRef<Ping> actor;
            try {
                actor = context().system().get(pingAddress, Ping.class);
            } catch (ActorNotFoundException | IllegalMsgTypeException e) {
                throw new RuntimeException(e);
            }
            context().dispatcher().scheduleAtFixedRate(() -> {
                actor.tell(new Ping(id++), self());
            }, 1, 1, TimeUnit.SECONDS);
        }

        @Override
        public void onReceive(Pong pong) {
            LOG.info("Receive: {} from {}", pong, sender());
        }

        @Override
        public MsgType<Pong> msgType() {
            return MsgType.of(Pong.class);
        }
    }

    public static class ServerActor extends Actor<Ping> {
        private static final Logger LOG = LoggerFactory.getLogger(ServerActor.class);

        protected ServerActor(ActorContext<Ping> context) {
            super(context);
        }

        @Override
        public void onReceive(Ping ping) {
            LOG.info("Receive: {} from {}", ping, sender());
            sender(Pong.class).tell(new Pong(ping.id), self());
        }

        @Override
        public MsgType<Ping> msgType() {
            return MsgType.of(Ping.class);
        }
    }
}
