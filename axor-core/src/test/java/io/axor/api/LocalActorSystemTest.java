package io.axor.api;

import io.axor.api.impl.ActorUnsafe;
import io.axor.commons.concurrent.EventStage;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.EventContext;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import io.axor.runtime.TypeReference;
import io.axor.testkit.LocalActorSystem;
import io.axor.testkit.actor.ActorTestKit;
import io.axor.testkit.actor.MockActorRef;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LocalActorSystemTest {
    private static final ActorTestKit testKit = new ActorTestKit(Duration.ofMillis(100));
    private static final EventContext.Key<String> key = new EventContext.Key<>(2, "test", "test",
            new EventContext.KeyMarshaller<String>() {
                @Override
                public String read(byte[] bytes, int off, int len) {
                    return new String(bytes, off, len);
                }

                @Override
                public byte[] write(String value) {
                    return value.getBytes();
                }
            });
    private static ActorSystem system;
    private static ActorRef<String> simpleReply;

    @BeforeClass
    public static void setup() {
        system = LocalActorSystem.getOrCreate("LocalActorSystemTest");
        simpleReply = system.start(SimpleReply::new, "simpleReply");
    }

    @AfterClass
    public static void teardown() {
        system.shutdownAsync().join();
    }

    @Test
    public void testAsk() throws Exception {
        MsgType<String> resType = MsgType.of(String.class);
        Duration timeout = Duration.ofSeconds(1);
        EventStage<String> future = ActorPatterns.ask(simpleReply, "hello", resType,
                timeout, system);
        Assert.assertEquals("hello", future.toFuture().get());
        future = ActorPatterns.ask(simpleReply, "world", resType, timeout, system);
        Assert.assertEquals("world", future.toFuture().get());
    }

    @Test
    public void testPubsub() throws Exception {
        Pubsub<String> pubsub = Pubsub.get("testPubsub", MsgType.of(String.class), system);
        var subscriber1 = testKit.mock(system.address("subscriber1"), String.class);
        var publisher = testKit.mock(system.address("publisher"), String.class);

        pubsub.subscribe(subscriber1);
        pubsub.publishToAll("hello", publisher);
        subscriber1.expectReceive("hello", publisher)
                .expectNoMsg();
        pubsub.subscribe(simpleReply);
        pubsub.publishToAll("world", publisher);
        subscriber1.expectReceive("world", publisher);
        publisher.expectReceive("world", simpleReply);

        for (int i = 0; i < 2; i++) pubsub.sendToOne("test", publisher);
        subscriber1.expectReceive("test", publisher);
        publisher.expectReceive("test", simpleReply);

        pubsub.unsubscribe(subscriber1);
        pubsub.unsubscribe(simpleReply);
        pubsub.publishToAll("hello", publisher);
        subscriber1.expectNoMsg();
        publisher.expectNoMsg();
    }

    @Test
    public void testDeadLetter() throws Exception {
        var subscriber1 = testKit.mock(system.address("DeadLetterSubscriber1"), DeadLetter.class);
        system.deadLetters().subscribe(subscriber1);
        ActorRef<String> actor = system.start(SimpleReply::new, "simpleReplyForDeadLetter");
        actor.tell("hello", ActorRef.noSender());
        subscriber1.expectNoMsg();
        system.stop(actor);
        actor.tell("hello", ActorRef.noSender());
        subscriber1.expectReceive((deadLetter, sender) -> {
            Assert.assertEquals("hello", deadLetter.message());
            Assert.assertTrue(deadLetter.sender().isNoSender());
            Assert.assertEquals(actor.address(), deadLetter.receiver());
        });
        system.deadLetters().unsubscribe(subscriber1);
    }

    @Test
    public void testSystemEvent() throws Exception {
        var subscriber1 = testKit.mock(system.address("SystemEventSubscriber1"), SystemEvent.class);
        system.systemEvents().subscribe(subscriber1);
        AtomicReference<ActorRef<String>> ref = new AtomicReference<>();
        ref.set(system.start(FailureOnStart::new, "failureOnStart"));
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_START, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        subscriber1.expectReceive(new SystemEvent.ActorStopped(ref.get()));
        Assert.assertTrue(ActorUnsafe.isStopped(ref.get()));

        ref.set(system.start(FailureOnReceive::new, "failureOnReceive"));
        subscriber1.expectReceive(new SystemEvent.ActorStarted(ref.get()));
        ref.get().tell("hello", ActorRef.noSender());
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_RECEIVE, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        subscriber1.expectReceive(new SystemEvent.ActorRestarted(ref.get()));
        Assert.assertFalse(ActorUnsafe.isStopped(ref.get()));
        system.stop(ref.get());
        subscriber1.expectReceive(new SystemEvent.ActorStopped(ref.get()));

        ref.set(system.start(FailureOnReStart::new, "failureOnReStart"));
        subscriber1.expectReceive(new SystemEvent.ActorStarted(ref.get()));
        ref.get().tell("hello", ActorRef.noSender());
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_RECEIVE, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_RESTART, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        subscriber1.expectReceive(new SystemEvent.ActorStopped(ref.get()));
        Assert.assertTrue(ActorUnsafe.isStopped(ref.get()));

        ref.set(system.start(FailureOnPreStop::new, "failureOnPreStop"));
        subscriber1.expectReceive(new SystemEvent.ActorStarted(ref.get()));
        system.stop(ref.get());
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_PRE_STOP, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        subscriber1.expectReceive(new SystemEvent.ActorStopped(ref.get()));
        Assert.assertTrue(ActorUnsafe.isStopped(ref.get()));

        ref.set(system.start(FailureOnPostStop::new, "failureOnPostStop"));
        subscriber1.expectReceive(new SystemEvent.ActorStarted(ref.get()));
        system.stop(ref.get());
        subscriber1.expectReceive(new SystemEvent.ActorStopped(ref.get()));
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_POST_STOP, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        Assert.assertTrue(ActorUnsafe.isStopped(ref.get()));
    }

    @Test
    public void testCacheGet() throws Exception {
        ActorRef<CharSequence> testCacheGet = system.start(c -> new Actor<>(c) {
            @Override
            public void onReceive(CharSequence charSequence) {
                // noop
            }

            @Override
            public MsgType<CharSequence> msgType() {
                return MsgType.of(CharSequence.class);
            }
        }, "testCacheGet");
        ActorAddress address = testCacheGet.address();
        Assert.assertSame(testCacheGet, system.get(address));
        Assert.assertThrows(IllegalMsgTypeException.class, () -> system.get(address,
                MsgType.of(String.class)));
        Assert.assertSame(testCacheGet, system.get(address, MsgType.of(CharSequence.class)));
        Assert.assertThrows(IllegalMsgTypeException.class, () -> system.get(address,
                MsgType.of(Object.class)));
        system.stop(testCacheGet);
        Assert.assertThrows(ActorNotFoundException.class, () -> system.get(address));
        Assert.assertThrows(ActorNotFoundException.class, () -> system.get(address,
                MsgType.of(String.class)));
    }

    @Test
    public void testWatch() throws Exception {
        CompletableFuture<SystemEvent.ActorStopped> future = new CompletableFuture<>();
        ActorRef<String> actor = system.start(SimpleReply::new, "SimpleReplyForTestWatch");
        ActorRef<CharSequence> testCacheGet = system.start(c -> new Actor<>(c) {

            @Override
            public void onStart() {
                context().watch(actor, List.of(SystemEvent.ActorStopped.class));
            }

            @Override
            public void preStop() {
                context().unwatch(actor);
            }

            @Override
            public void onReceive(CharSequence charSequence) {
                // noop
            }

            @Override
            public void onSignal(Signal signal) {
                if (signal instanceof SystemEvent.ActorStopped stopped) {
                    future.complete(stopped);
                } else {
                    future.completeExceptionally(new RuntimeException(signal.toString()));
                }
            }

            @Override
            public MsgType<CharSequence> msgType() {
                return MsgType.of(CharSequence.class);
            }
        }, "testWatch");
        Thread.sleep(10);
        system.stop(actor);
        Assert.assertEquals(new SystemEvent.ActorStopped(actor), future.get(100,
                TimeUnit.MILLISECONDS));
    }

    @Test
    public void testCreateChild() throws Exception {
        var subscriber1 = testKit.mock(system.address("SystemEventSubscriberForTestChild"),
                SystemEvent.class);
        system.systemEvents().subscribe(subscriber1);
        var mock = testKit.mock(system.address("testCreateChild"), String.class);
        ActorRef<String> actor = system.start(HasChildActor::new, "hasChild");
        actor.tell("Hello", mock);
        ActorRef<?> child = system.get(system.address("child"));
        mock.expectReceive("Hello", child);
        mock.expectNoMsg();
        system.stop(actor).toFuture().syncUninterruptibly();
        subscriber1.expectReceive(new SystemEvent.ActorStarted(actor));
        subscriber1.expectReceive(new SystemEvent.ActorStarted(child));
        subscriber1.expectReceive(new SystemEvent.ActorStopped(child));
        subscriber1.expectReceive(new SystemEvent.ActorStopped(actor));
        subscriber1.expectNoMsg();
    }

    @Test
    public void testContextPropagation() throws Exception {
        MsgType<Map<ActorAddress, String>> msgType = MsgType.of(new TypeReference<>() {
        });
        MockActorRef<Map<ActorAddress, String>> subscriber1 = testKit.
                mock(system.address("testContextPropagation"), msgType);
        ActorRef<Map<ActorAddress, String>> propagation1 =
                system.start(c -> new ContextPropagation(c, subscriber1), "propagation1");
        ActorRef<Map<ActorAddress, String>> propagation2 =
                system.start(c -> new ContextPropagation(c, propagation1), "propagation2");
        ActorRef<Map<ActorAddress, String>> propagation3 =
                system.start(c -> new ContextPropagation(c, propagation2), "propagation3");

        try (var scope = EventContext.INITIAL.with(key, "Test").openScope()) {
            Assert.assertEquals("Test", EventContext.current().get(key));
            propagation3.tell(Collections.emptyMap());
        }
        MockActorRef<Map<ActorAddress, String>>.MsgAndSender poll = subscriber1.poll();
        Assert.assertEquals(poll.getSender(), propagation1);
        Assert.assertEquals(new HashMap<>(Map.of(
                propagation1.address(), "Test",
                propagation2.address(), "Test",
                propagation3.address(), "Test"
        )), poll.getMsg());
    }

    private void testContextDistribute() throws Exception {

    }

    private static abstract class StringActor extends Actor<String> {

        protected StringActor(ActorContext<String> context) {
            super(context);
        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }

        @Override
        public void onReceive(String s) {

        }
    }

    public static class SimpleReply extends StringActor {

        protected SimpleReply(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onReceive(String s) {
            context().sender()
                    .cast(MsgType.of(String.class))
                    .tell(s, self());
        }
    }

    public static class FailureOnStart extends StringActor {

        protected FailureOnStart(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onStart() {
            throw new RuntimeException("Testing: FailureOnStart");
        }
    }

    public static class FailureOnReceive extends StringActor {

        protected FailureOnReceive(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onReceive(String s) {
            throw new RuntimeException("Testing: FailureOnReceive");
        }
    }

    public static class FailureOnPreStop extends StringActor {

        protected FailureOnPreStop(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void preStop() {
            throw new RuntimeException("Testing: FailureOnPreStop");
        }
    }

    public static class FailureOnPostStop extends StringActor {
        protected FailureOnPostStop(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void postStop() {
            throw new RuntimeException("Testing: FailureOnPostStop");
        }
    }

    public static class FailureOnReStart extends StringActor {

        protected FailureOnReStart(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onReceive(String s) {
            throw new RuntimeException("Testing: FailureOnReStart");
        }

        @Override
        public void onRestart() {
            throw new RuntimeException("Testing: FailureOnReStart");
        }
    }

    public static class HasChildActor extends StringActor {
        private ActorRef<String> child;

        protected HasChildActor(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onStart() {
            child = context().startChild(SimpleReply::new, "child");
            Assert.assertSame(context().dispatcher(), ActorUnsafe.getDispatcher(child));
        }

        @Override
        public void onReceive(String s) {
            child.tell(s, sender());
        }

        @Override
        public void preStop() {
            Assert.assertFalse(ActorUnsafe.isStopped(child));
        }

        @Override
        public void postStop() {
            Assert.assertTrue(ActorUnsafe.isStopped(child));
        }
    }

    public static class ContextPropagation extends Actor<Map<ActorAddress, String>> {
        private static final Logger LOG = LoggerFactory.getLogger(ContextPropagation.class);
        private final ActorRef<Map<ActorAddress, String>> sendTo;

        protected ContextPropagation(ActorContext<Map<ActorAddress, String>> context,
                                     ActorRef<Map<ActorAddress, String>> sendTo) {
            super(context);
            this.sendTo = sendTo;
        }

        @Override
        public void onReceive(Map<ActorAddress, String> s) {
            LOG.info("Receive {} from {}", s, sender());
            var map = new HashMap<>(s);
            map.put(self().address(), EventContext.current().get(key));
            sendTo.tell(map, self());
        }

        @Override
        public MsgType<Map<ActorAddress, String>> msgType() {
            return MsgType.of(new TypeReference<>() {
            });
        }
    }
}
