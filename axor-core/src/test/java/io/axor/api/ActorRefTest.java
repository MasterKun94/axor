package io.axor.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.runtime.MsgType;
import io.axor.runtime.StreamServer;
import io.axor.runtime.stream.grpc.GrpcStreamServer;
import io.grpc.ServerBuilder;
import io.masterkun.stateeasy.concurrent.EventStage;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class ActorRefTest {

    private final static String system = "ActorRefTest";
    private static ActorSystem system1;
    private static ActorSystem system2;
    private static StreamServer server1;
    private static StreamServer server2;

    @BeforeClass
    public static void setup() throws Exception {
        server1 = new GrpcStreamServer(system, ServerBuilder.forPort(10013));
        server1.start();
        server2 = new GrpcStreamServer(system, ServerBuilder.forPort(10014));
        server2.start();
        Config config = ConfigFactory.load().resolve();
        system1 = ActorSystem.create(system, config, server1);
        system2 = ActorSystem.create(system, config, server2);
    }

    @AfterClass
    public static void teardown() {
        system1.shutdownAsync().join();
        system1.shutdownAsync().join();
    }

    private static StreamServer startServer(int port) {
        return new GrpcStreamServer(system, ServerBuilder.forPort(port));
    }

    @Test
    public void test() throws Exception {
        system1.start(SimpleReplyActor::new, "SimpleReplyActor");

        ActorAddress address = ActorAddress.create(system, "localhost", 10013, "SimpleReplyActor");
        ActorRef<Message1> actor = system2.get(
                address,
                Message1.class);
        EventStage<Message2> hello = ActorPatterns.ask(
                actor, new Message1(1, "hello"),
                MsgType.of(Message2.class), Duration.ofSeconds(1), system2);
        Assert.assertEquals(new Message2(1, "hello"), hello.toFuture().get());
        Assert.assertSame(actor, system2.get(actor.address(), Message1.class));
        Assert.assertSame(actor, system2.get(actor.address()));

        ActorRef<Message2> printActor = system2.start(PrintActor::new, "PrintActor");
        actor.tell(new Message1(2, "hello from print actor"), printActor);
        Thread.sleep(100);
        actor = null;
        System.gc();
        Assert.assertThrows(RuntimeException.class, () -> system2.get(address));
        Thread.sleep(100);
        System.out.println("Garbage collected");
        System.gc();
        Thread.sleep(100);
        System.out.println("Garbage collected");

    }

    public record Message1(int i, String value) {
    }

    public record Message2(int i, String value) {
    }

    public static class Node1 {
        public static void main(String[] args) {
            var server = startServer(8080);
            ActorSystem system = ActorSystem.create("node1", ConfigFactory.load(), server);
            ActorRef<Message1> simpleReply = system.start(SimpleReplyActor::new,
                    "SimpleReplyActor");
        }
    }

    public static class Node2 {
        public static void main(String[] args) throws Exception {
            var server = startServer(8081);
            ActorSystem system = ActorSystem.create("node2", ConfigFactory.load(), server);
            ActorRef<Message2> print = system.start(PrintActor::new, "PrintActor");
            ActorAddress p = ActorAddress.create("node1", "localhost", 8080, "SimpleReplyActor");
            ActorRef<Message1> simpleReply = system.get(p, Message1.class);

            while (true) {
                simpleReply.tell(new Message1(1, "Hello World"), print);
                Thread.sleep(1000);
            }
        }
    }

    private static class SimpleReplyActor extends Actor<Message1> {
        private static final Logger LOG = LoggerFactory.getLogger(SimpleReplyActor.class);

        protected SimpleReplyActor(ActorContext<Message1> context) {
            super(context);
        }

        @Override
        public void onStart() {
            LOG.info("{} is started", this);
        }

        @Override
        public void onReceive(Message1 msg) {
            System.out.println(msg);
            context().sender()
                    .cast(MsgType.of(Message2.class))
                    .tell(new Message2(msg.i, msg.value), context().self());
        }

        @Override
        public void postStop() {
            LOG.info("{} is stopped", this);
        }

        @Override
        public MsgType<Message1> msgType() {
            return MsgType.of(Message1.class);
        }
    }

    private static class PrintActor extends Actor<Message2> {
        private static final Logger LOG = LoggerFactory.getLogger(PrintActor.class);

        protected PrintActor(ActorContext<Message2> context) {
            super(context);
        }

        @Override
        public void onStart() {
            LOG.info("{} is started", this);
        }

        @Override
        public void onReceive(Message2 msg) {
            LOG.info("{} received: {}", context().self(), msg);
        }

        @Override
        public void postStop() {
            LOG.info("{} is stopped", this);
        }

        @Override
        public MsgType<Message2> msgType() {
            return MsgType.of(Message2.class);
        }
    }

}
