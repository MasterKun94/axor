package io.masterkun.axor.api;

import io.masterkun.axor.api.impl.LocalActorRefUnsafe;
import io.masterkun.axor.exception.ActorNotFoundException;
import io.masterkun.axor.exception.IllegalMsgTypeException;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.testkit.LocalActorSystem;
import io.masterkun.axor.testkit.actor.ActorTestKit;
import io.masterkun.stateeasy.concurrent.EventStage;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LocalActorSystemTest {
    private static final ActorTestKit testKit = new ActorTestKit(Duration.ofMillis(100));
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
        Pubsub<String> pubsub = Pubsub.create(system, MsgType.of(String.class));
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
        Assert.assertTrue(LocalActorRefUnsafe.isStopped(ref.get()));

        ref.set(system.start(FailureOnReceive::new, "failureOnReceive"));
        subscriber1.expectReceive(new SystemEvent.ActorStarted(ref.get()));
        ref.get().tell("hello", ActorRef.noSender());
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_RECEIVE, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        subscriber1.expectReceive(new SystemEvent.ActorRestarted(ref.get()));
        Assert.assertFalse(LocalActorRefUnsafe.isStopped(ref.get()));
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
        Assert.assertTrue(LocalActorRefUnsafe.isStopped(ref.get()));

        ref.set(system.start(FailureOnPreStop::new, "failureOnPreStop"));
        subscriber1.expectReceive(new SystemEvent.ActorStarted(ref.get()));
        system.stop(ref.get());
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_PRE_STOP, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        subscriber1.expectReceive(new SystemEvent.ActorStopped(ref.get()));
        Assert.assertTrue(LocalActorRefUnsafe.isStopped(ref.get()));

        ref.set(system.start(FailureOnPostStop::new, "failureOnPostStop"));
        subscriber1.expectReceive(new SystemEvent.ActorStarted(ref.get()));
        system.stop(ref.get());
        subscriber1.expectReceive(SystemEvent.ActorError.class, ((event, sender) -> {
            Assert.assertEquals(SystemEvent.ActorAction.ON_POST_STOP, event.action());
            Assert.assertEquals(ref.get(), event.actor());
        }));
        subscriber1.expectReceive(new SystemEvent.ActorStopped(ref.get()));
        Assert.assertTrue(LocalActorRefUnsafe.isStopped(ref.get()));
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
}
