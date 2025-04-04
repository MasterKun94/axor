package io.axor.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.commons.concurrent.EventStage;
import io.axor.runtime.EventContext;
import io.axor.runtime.MsgType;
import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DistributeActorSystemTest {
    private static final EventContext.Key<String> key = new EventContext.Key<>(2, "test", "test",
            new EventContext.KeyMarshaller<>() {
                @Override
                public String read(byte[] bytes, int off, int len) {
                    return new String(bytes, off, len);
                }

                @Override
                public byte[] write(String value) {
                    return value.getBytes();
                }
            });
    private static ActorTestKit testKit = new ActorTestKit(Duration.ofMillis(100));
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

        Assert.assertTrue(queue.poll() instanceof SystemEvent.ActorStarted);
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
        try (var scope = EventContext.INITIAL.with(key, "Test").openScope()) {
            Assert.assertEquals("Test", EventContext.current().get(key));
            actor3.tell("");
        }
        MockActorRef<String>.MsgAndSender poll = mock.poll();
        Assert.assertEquals(poll.getSender(), actor1);
        Assert.assertEquals(
                Stream.of(actor3, actor2, actor1).map(a -> "," + a.address() + "=Test").collect(Collectors.joining()),
                poll.getMsg()
        );
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
            sendTo.tell(s + "," + self().address() + "=" + EventContext.current().get(key), self());
        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }
    }
}
