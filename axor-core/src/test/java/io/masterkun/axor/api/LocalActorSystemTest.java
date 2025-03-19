package io.masterkun.axor.api;

import io.masterkun.axor.api.impl.LocalActorRef;
import io.masterkun.axor.exception.ActorNotFoundException;
import io.masterkun.axor.exception.IllegalMsgTypeException;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.testkit.LocalActorSystem;
import io.masterkun.axor.testkit.MessageBufferActorRef;
import io.masterkun.stateeasy.concurrent.EventStage;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class LocalActorSystemTest {
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
        MessageBufferActorRef<String> subscriber1 = new MessageBufferActorRef<>(
                ActorAddress.create(system.name(), system.publishAddress(), "subscriber1"),
                MsgType.of(String.class));
        MessageBufferActorRef<String> publisher = new MessageBufferActorRef<>(
                ActorAddress.create(system.name(), system.publishAddress(), "publisher"),
                MsgType.of(String.class));

        pubsub.subscribe(subscriber1);
        pubsub.publishToAll("hello", publisher);
        Thread.sleep(10);
        Assert.assertEquals("hello", subscriber1.pollMessage(10, TimeUnit.MILLISECONDS));
        Assert.assertNull(publisher.pollMessage(10, TimeUnit.MILLISECONDS));
        pubsub.subscribe(simpleReply);
        pubsub.publishToAll("world", publisher);
        Assert.assertEquals("world", subscriber1.pollMessage(10, TimeUnit.MILLISECONDS));
        Assert.assertEquals("world", publisher.pollMessage(10, TimeUnit.MILLISECONDS));

        for (int i = 0; i < 2; i++) pubsub.sendToOne("test", publisher);
        Assert.assertEquals("test", subscriber1.pollMessage(10, TimeUnit.MILLISECONDS));
        Assert.assertEquals("test", publisher.pollMessage(10, TimeUnit.MILLISECONDS));

        pubsub.unsubscribe(subscriber1);
        pubsub.unsubscribe(simpleReply);
        pubsub.publishToAll("hello", publisher);
        Assert.assertNull(subscriber1.pollMessage(10, TimeUnit.MILLISECONDS));
        Assert.assertNull(publisher.pollMessage(10, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDeadLetter() throws Exception {
        MessageBufferActorRef<DeadLetter> subscriber1 = new MessageBufferActorRef<>(system,
                "DeadLetterSubscriber1", MsgType.of(DeadLetter.class));
        system.deadLetters().subscribe(subscriber1);
        ActorRef<String> actor = system.start(SimpleReply::new, "simpleReplyForDeadLetter");
        actor.tell("hello", ActorRef.noSender());
        Assert.assertNull(subscriber1.pollMessage(10, TimeUnit.MILLISECONDS));
        system.stop(actor);
        actor.tell("hello", ActorRef.noSender());
        DeadLetter deadLetter = subscriber1.pollMessage(10, TimeUnit.MILLISECONDS);
        Assert.assertEquals("hello", deadLetter.message());
        Assert.assertTrue(deadLetter.sender().isNoSender());
        Assert.assertEquals(actor.address(), deadLetter.receiver());

        system.deadLetters().unsubscribe(subscriber1);
    }

    @Test
    public void testSystemEvent() throws Exception {
        MessageBufferActorRef<SystemEvent> subscriber1 = new MessageBufferActorRef<>(system,
                "SystemEventSubscriber1", MsgType.of(SystemEvent.class));
        system.systemEvents().subscribe(subscriber1);
        ActorRef<String> actor = system.start(FailureOnStart::new, "failureOnStart");
        SystemEvent.ActorError event = (SystemEvent.ActorError) subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS);
        Assert.assertEquals(SystemEvent.ActorAction.ON_START, event.action());
        Assert.assertEquals(actor, event.actor());
        Assert.assertEquals(new SystemEvent.ActorStopped(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        Assert.assertTrue(((LocalActorRef<String>) actor).isStopped());

        actor = system.start(FailureOnReceive::new, "failureOnReceive");
        Assert.assertEquals(new SystemEvent.ActorStarted(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        actor.tell("hello", ActorRef.noSender());
        event = (SystemEvent.ActorError) subscriber1.pollMessage(10, TimeUnit.MILLISECONDS);
        Assert.assertEquals(SystemEvent.ActorAction.ON_RECEIVE, event.action());
        Assert.assertEquals(actor, event.actor());
        Assert.assertEquals(new SystemEvent.ActorRestarted(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        Assert.assertFalse(((LocalActorRef<String>) actor).isStopped());
        system.stop(actor);
        Assert.assertEquals(new SystemEvent.ActorStopped(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));

        actor = system.start(FailureOnReStart::new, "failureOnReStart");
        Assert.assertEquals(new SystemEvent.ActorStarted(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        actor.tell("hello", ActorRef.noSender());
        event = (SystemEvent.ActorError) subscriber1.pollMessage(10, TimeUnit.MILLISECONDS);
        Assert.assertEquals(SystemEvent.ActorAction.ON_RECEIVE, event.action());
        Assert.assertEquals(actor, event.actor());
        event = (SystemEvent.ActorError) subscriber1.pollMessage(10, TimeUnit.MILLISECONDS);
        Assert.assertEquals(SystemEvent.ActorAction.ON_RESTART, event.action());
        Assert.assertEquals(actor, event.actor());
        Assert.assertEquals(new SystemEvent.ActorStopped(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        Assert.assertTrue(((LocalActorRef<String>) actor).isStopped());

        actor = system.start(FailureOnPreStop::new, "failureOnPreStop");
        Assert.assertEquals(new SystemEvent.ActorStarted(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        system.stop(actor);
        event = (SystemEvent.ActorError) subscriber1.pollMessage(10, TimeUnit.MILLISECONDS);
        Assert.assertEquals(SystemEvent.ActorAction.ON_PRE_STOP, event.action());
        Assert.assertEquals(actor, event.actor());
        Assert.assertEquals(new SystemEvent.ActorStopped(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        Assert.assertTrue(((LocalActorRef<String>) actor).isStopped());

        actor = system.start(FailureOnPostStop::new, "failureOnPostStop");
        Assert.assertEquals(new SystemEvent.ActorStarted(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        system.stop(actor);
        event = (SystemEvent.ActorError) subscriber1.pollMessage(10, TimeUnit.MILLISECONDS);
        Assert.assertEquals(SystemEvent.ActorAction.ON_POST_STOP, event.action());
        Assert.assertEquals(actor, event.actor());
        Assert.assertEquals(new SystemEvent.ActorStopped(actor), subscriber1.pollMessage(10,
                TimeUnit.MILLISECONDS));
        Assert.assertTrue(((LocalActorRef<String>) actor).isStopped());

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
            throw new RuntimeException("FailureOnStart");
        }
    }

    public static class FailureOnReceive extends StringActor {

        protected FailureOnReceive(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onReceive(String s) {
            throw new RuntimeException("FailureOnReceive");
        }
    }

    public static class FailureOnPreStop extends StringActor {

        protected FailureOnPreStop(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void preStop() {
            throw new RuntimeException("FailureOnPreStop");
        }
    }

    public static class FailureOnPostStop extends StringActor {
        protected FailureOnPostStop(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void postStop() {
            throw new RuntimeException("FailureOnPostStop");
        }
    }

    public static class FailureOnReStart extends StringActor {

        protected FailureOnReStart(ActorContext<String> context) {
            super(context);
        }

        @Override
        public void onReceive(String s) {
            throw new RuntimeException("FailureOnReStart");
        }

        @Override
        public void onRestart() {
            throw new RuntimeException("FailureOnReStart");
        }
    }
}
