package io.axor.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.impl.ActorUnsafe;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.concurrent.Try;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventContextKeyMarshaller;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
import io.axor.runtime.serde.kryo.KryoSerdeFactory;
import io.axor.runtime.stream.grpc.StreamRecord;
import io.axor.testkit.actor.ActorTestKit;
import io.axor.testkit.actor.MockActorRef;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DistributeActorSystemTest {
    private static final EventContext.Key<String> key = new EventContext.Key<>(2,
            "test", "test", EventContextKeyMarshaller.STRING);
    private static final EventContext.Key<String> key2 = new EventContext.Key<>(3,
            "test2", "test2", EventContextKeyMarshaller.STRING);
    private static final ActorTestKit testKit = new ActorTestKit(Duration.ofMillis(500));
    private static ActorSystem system1;
    private static ActorSystem system2;
    private static ActorRef<String> simpleReply1;
    private static MockActorRef<SystemEvent> systemEventListener1;
    private static MockActorRef<DeadLetter> deadLetterListener1;
    private static MockActorRef<SystemEvent> systemEventListener2;
    private static MockActorRef<DeadLetter> deadLetterListener2;

    @BeforeClass
    public static void setup() throws Exception {
        Node1.start();
        Node2.start();
    }

    @AfterClass
    public static void teardown() throws Exception {
        Node1.stop();
        Node2.stop();
    }

    @Test
    public void testAsk() throws Exception {
        ActorRef<String> simpleReply = system2.get(simpleReply1.address(),
                MsgType.of(String.class));
        EventStage<String> future = ActorPatterns.ask(simpleReply, "hello",
                MsgType.of(String.class), Duration.ofSeconds(1), system2);
        Assert.assertEquals("hello", future.toFuture().get());
        SystemEvent event;
        Queue<SystemEvent> queue = new LinkedList<>();
        while ((event = systemEventListener1.pollMessage()) != null) {
            queue.add(event);
            System.out.println(event);
        }
        Assert.assertEquals(new SystemEvent.ActorStarted(simpleReply1), queue.poll());
        assertStreamEvent(queue.poll(), SystemEvent.StreamInOpened.class,
                null, MsgType.of(String.class),
                simpleReply.address(), MsgType.of(String.class),
                null);
        assertStreamEvent(queue.poll(), SystemEvent.StreamOutOpened.class,
                null, MsgType.of(String.class),
                simpleReply.address(), MsgType.of(String.class),
                null);
        assertStreamEvent(queue.poll(), SystemEvent.StreamInClosed.class,
                null, MsgType.of(String.class),
                simpleReply.address(), MsgType.of(String.class),
                StatusCode.COMPLETE.toStatus());
        assertStreamEvent(queue.poll(), SystemEvent.StreamOutClosed.class,
                null, MsgType.of(String.class),
                simpleReply.address(), MsgType.of(String.class),
                StatusCode.COMPLETE.toStatus());
        Assert.assertTrue(queue.isEmpty());

        System.out.println("---");
        while ((event = systemEventListener2.pollMessage()) != null) {
            queue.add(event);
            System.out.println(event);
        }

        SystemEvent poll = queue.poll();
        Assert.assertTrue(poll instanceof SystemEvent.ActorStarted);
        assertStreamEvent(queue.poll(), SystemEvent.StreamOutOpened.class,
                simpleReply.address(), MsgType.of(String.class),
                null, MsgType.of(String.class),
                null);
        assertStreamEvent(queue.poll(), SystemEvent.StreamInOpened.class,
                simpleReply.address(), MsgType.of(String.class),
                null, MsgType.of(String.class),
                null);
        assertStreamEvent(queue.poll(), SystemEvent.StreamOutClosed.class,
                simpleReply.address(), MsgType.of(String.class),
                null, MsgType.of(String.class),
                StatusCode.COMPLETE.toStatus());
        Assert.assertTrue(queue.poll() instanceof SystemEvent.ActorStopped);
        assertStreamEvent(queue.poll(), SystemEvent.StreamInClosed.class,
                simpleReply.address(), MsgType.of(String.class),
                null, MsgType.of(String.class),
                StatusCode.COMPLETE.toStatus());
        Assert.assertTrue(queue.isEmpty());
    }

    @Test
    public void testTellWithAck() throws Exception {
        ActorRef<String> actor = system1.start(c -> new Actor<>(c) {
            @Override
            public void onReceive(String t) {
            }

            @Override
            public MsgType<String> msgType() {
                return MsgType.of(String.class);
            }
        }, "testTellWithAck");
        ActorRef<String> ref = system2.get(actor.address(), String.class);
        EventStage<Void> stage = ActorPatterns.tellWithAck(ref, "Hello", Duration.ofMillis(1000),
                system2);
        Assert.assertEquals(Try.success(null), stage.toFuture().syncUninterruptibly());
    }

    @Test
    public void testContextPropagation() throws Exception {
        MockActorRef<String> mock = new ActorTestKit(Duration.ofMillis(1000)).
                mock(system1.address("testContextPropagation"), MsgType.of(String.class));
        ActorRef<String> actor1 = system1.start(s -> new ContextPropagation(s, mock),
                "propagation1");
        ActorRef<String> actor1Ref = system2.get(actor1.address(), MsgType.of(String.class));
        ActorRef<String> actor2 = system2.start(s -> new ContextPropagation(s, actor1Ref),
                "propagation2");
        ActorRef<String> actor2Ref = system1.get(actor2.address(), MsgType.of(String.class));
        ActorRef<String> actor3 = system1.start(s -> new ContextPropagation(s, actor2Ref),
                "propagation3");
        try (var scope = EventContext.INITIAL.with(key, "A").with(key2, "A2", 2).openScope()) {
            Assert.assertEquals("A", EventContext.current().get(key));
            actor3.tell("");
        }
        MockActorRef.MsgAndRef<String> poll = mock.pollFromReceived();
        Assert.assertEquals(poll.getRef(), actor1);
        Assert.assertEquals(
                String.format(",%s=A:A2,%s=A:A2,%s=A:null", actor3.address(), actor2.address(),
                        actor1.address()),
                poll.getMsg()
        );
        try (var scope = EventContext.INITIAL.with(key, "B").with(key2, "B2", 1).openScope()) {
            Assert.assertEquals("B", EventContext.current().get(key));
            actor3.tell("");
        }
        poll = mock.pollFromReceived();
        Assert.assertEquals(poll.getRef(), actor1);
        Assert.assertEquals(
                String.format(",%s=B:B2,%s=B:null,%s=B:null", actor3.address(), actor2.address(),
                        actor1.address()),
                poll.getMsg()
        );
    }

    @Test
    public void testSignal() throws Exception {
        system1.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
                .addInitializer(kryo -> kryo.register(TestSignal.class, 21101));
        system2.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
                .addInitializer(kryo -> kryo.register(TestSignal.class, 21101));
        BlockingQueue<StreamRecord.ContextSignal<?>> signals = new LinkedBlockingQueue<>();
        ActorRef<String> ref = system1.start(c -> new Actor<>(c) {
            @Override
            public void onReceive(String s) {
                throw new RuntimeException(s);
            }

            @Override
            public void onSignal(Signal signal) {
                signals.add(new StreamRecord.ContextSignal<>(EventContext.current(), signal));
            }

            @Override
            public MsgType<String> msgType() {
                return MsgType.of(String.class);
            }
        }, "testSignal");
        ActorRef<String> actor = system2.get(ref.address(), MsgType.of(String.class));
        ActorUnsafe.signal(actor, new TestSignal("Hello"));
        EventContext.set(EventContext.INITIAL.with(key, "Hi Hi"));
        ActorUnsafe.signal(actor, new TestSignal("World"));
        EventContext.set(EventContext.INITIAL);
        ActorUnsafe.signal(actor, new TestSignal("!!!"));
        Assert.assertEquals(new StreamRecord.ContextSignal<>(EventContext.INITIAL,
                        new TestSignal("Hello")),
                signals.poll(300, TimeUnit.MILLISECONDS));
        Assert.assertEquals(new StreamRecord.ContextSignal<>(EventContext.INITIAL.with(key, "Hi Hi"),
                        new TestSignal("World")),
                signals.poll(10, TimeUnit.MILLISECONDS));
        Assert.assertEquals(new StreamRecord.ContextSignal<>(EventContext.INITIAL,
                        new TestSignal("!!!")),
                signals.poll(10, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testAskWithDeadLetter() throws Exception {
        ActorAddress deadActorAddr = ActorAddress.create(system1.name(), system1.publishAddress()
                , "deadActor");
        ActorRef<String> deadActor = system2.get(deadActorAddr, MsgType.of(String.class));
        EventStage<String> future = ActorPatterns.ask(deadActor, "hello",
                MsgType.of(String.class), Duration.ofMillis(100), system2);
        Assert.assertThrows(TimeoutException.class, () -> {
            try {
                future.toFuture().get();
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
        DeadLetter polled = deadLetterListener1.pollMessage();
        Assert.assertEquals(deadActorAddr, polled.receiver());
        Assert.assertEquals("hello", polled.message());

        Assert.assertNull(deadLetterListener1.pollMessage());
        Assert.assertNull(deadLetterListener2.pollMessage());

        systemEventListener1.clear();
        systemEventListener2.clear();
    }

    @Test
    public void testSessionAsk() throws Exception {
        MockActorRef<String> reply = testKit.mock("sessionReply", String.class, system1);
        MockActorRef<String> clientRef = testKit.mock(system2.address("sessionClient"),
                String.class);
        clientRef.setTimeout(Duration.ofSeconds(2));
        ActorRef<String> replayRef = system2.get(reply.address(), MsgType.of(String.class));
        ActorRef<String> sessionAsk =
                system2.start(actorContext -> new SessionAskActor(actorContext, replayRef),
                        "sessionAsk");
        ActorRef<String> sessionAskRef = system1.get(sessionAsk.address(), String.class);

        sessionAsk.tell("Hello", clientRef);
        reply.expectReceive("Hello");
        sessionAskRef.tell("World", reply);
        clientRef.expectReceive("Receive Success msg: World");

        sessionAsk.tell("Hello2", clientRef);
        reply.expectReceive("Hello2");
        clientRef.expectReceive("Receive Error: " + new TimeoutException());

        sessionAsk.tell("Hello3", clientRef);
        reply.expectReceive("Hello3");
        sessionAskRef.tell("World3", reply);
        clientRef.expectReceive("Receive Success msg: World3");

        sessionAsk.tell("Hello3", clientRef);
        reply.expectReceive("Hello3");
    }

    public void assertStreamEvent(SystemEvent event, Class<? extends SystemEvent.StreamEvent> type,
                                  ActorAddress remoteAddress, MsgType<?> remoteMsgType,
                                  ActorAddress selfAddress, MsgType<?> selfMsgType,
                                  Status status) {
        Assert.assertNotNull(event);
        Assert.assertEquals(type, event.getClass());
        SystemEvent.StreamEvent streamEvent = (SystemEvent.StreamEvent) event;
        if (remoteAddress != null) {
            Assert.assertEquals(remoteAddress, streamEvent.remoteAddress());
        }
        if (remoteMsgType != null) {
            Assert.assertEquals(remoteMsgType, streamEvent.remoteMsgType());
        }
        if (selfAddress != null) {
            Assert.assertEquals(selfAddress, streamEvent.selfAddress());
        }
        if (selfMsgType != null) {
            Assert.assertEquals(selfMsgType, streamEvent.selfMsgType());
        }
        if (status != null) {
            if (event instanceof SystemEvent.StreamInClosed closed) {
                Assert.assertEquals(closed.status().code(), status.code());
            } else if (event instanceof SystemEvent.StreamOutClosed closed) {
                Assert.assertEquals(closed.status().code(), status.code());
            }
        }
    }

    public static class Node1 {
        public static void start() throws Exception {
            Config config1 = ConfigFactory
                    .load(ConfigFactory.parseString("axor.network.bind.port = " + 10123))
                    .resolve();
            system1 = ActorSystem.create("test", config1);
            Thread.sleep(1);
            systemEventListener1 = testKit.mock(
                    system1.address("deadLetterListener"),
                    SystemEvent.class
            );
            deadLetterListener1 = testKit.mock(
                    system1.address("deadLetterListener"),
                    DeadLetter.class);
            system1.systemEvents().subscribe(systemEventListener1);
            system1.deadLetters().subscribe(deadLetterListener1);
            simpleReply1 = system1.start(LocalActorSystemTest.SimpleReply::new, "simpleReply");
        }

        public static void stop() {
            system1.shutdownAsync().join();
        }

        public static void main(String[] args) throws Exception {
            start();
            while (true) {
                SystemEvent event = systemEventListener1.pollMessage();
                if (event != null) {
                    System.out.println(event);
                }
            }
        }
    }

    public static class Node2 {
        public static void start() throws Exception {
            Config config2 = ConfigFactory
                    .load(ConfigFactory.parseString("axor.network.bind.port = " + 10124))
                    .resolve();
            system2 = ActorSystem.create("test", config2);
            Thread.sleep(1);
            systemEventListener2 = testKit.mock(
                    system2.address("deadLetterListener"),
                    SystemEvent.class
            );
            deadLetterListener2 = testKit.mock(
                    system2.address("deadLetterListener"),
                    DeadLetter.class);
            system2.systemEvents().subscribe(systemEventListener2);
            system2.deadLetters().subscribe(deadLetterListener2);
        }

        public static void stop() {
            system2.shutdownAsync().join();
        }

        public static void main(String[] args) throws Exception {
            start();
            ActorAddress address = ActorAddress.create(system2.name(),
                    system2.publishAddress().host(), 10123, "simpleReply");
            ActorRef<String> simpleReply = system2.get(address, MsgType.of(String.class));
            EventStage<String> future = ActorPatterns.ask(simpleReply, "hello",
                    MsgType.of(String.class), Duration.ofSeconds(1), system2);
            Assert.assertEquals("hello", future.toFuture().get());
            Thread.sleep(1000);
            SystemEvent event;
            while ((event = systemEventListener2.pollMessage()) != null) {
                System.out.println(event);
            }
            Thread.sleep(1000);
//            stop();
        }
    }

    public static class ContextPropagation extends Actor<String> {
        private static final Logger LOG = LoggerFactory.getLogger(ContextPropagation.class);
        private final ActorRef<String> sendTo;

        protected ContextPropagation(ActorContext<String> context, ActorRef<String> sendTo) {
            super(context);
            this.sendTo = sendTo;
        }

        @Override
        public void onReceive(String s) {
            LOG.info("Receive {} from {}", s, sender());
            EventContext ctx = EventContext.current();
            sendTo.tell(s + "," + self().address() + "=" + ctx.get(key) + ":" + ctx.get(key2),
                    self());
        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }
    }

    public record TestSignal(String hello) implements Signal {
    }

    private static class SessionAskActor extends Actor<String> {
        private final ActorRef<String> ref;

        public SessionAskActor(ActorContext<String> actorContext,
                               ActorRef<String> ref) {
            super(actorContext);
            this.ref = ref;
        }

        @Override
        public void onReceive(String msg) {
            ActorRef<String> sender = sender(String.class);
            ref.tell(msg);
            context().sessions().expectReceiveFrom(ref)
                    .msgType(MsgType.of(String.class))
                    .msgPredicate(s -> true)
                    .listen(Duration.ofSeconds(1))
                    .observe(s -> {
                        sender.tell("Receive Success msg: " + s);
                    }, e -> {
                        sender.tell("Receive Error: " + e.toString());
                    });
        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }
    }
}
