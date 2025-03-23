package io.masterkun.axor.testkit.actor;

import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.Signal;
import io.masterkun.axor.api.impl.ForwardingActorRef;
import org.junit.Assert;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.masterkun.axor.testkit.actor.MsgAssertions.eq;

public class MockActorRef<T> extends ForwardingActorRef<T> {
    private final BlockingQueue<MsgAndSeder> queue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Signal> signals = new LinkedBlockingQueue<>();
    private final long pollTimeout;

    public MockActorRef(ActorRef<T> delegate, long pollTimeout) {
        super(delegate);
        this.pollTimeout = pollTimeout;
    }

    private static <T> T take(BlockingQueue<T> queue, long timeout) {
        T poll;
        try {
            poll = queue.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (poll == null) {
            throw new RuntimeException("timeout");
        }
        return poll;
    }

    @Override
    public void tell(T value, ActorRef<?> sender) {
        queue.add(new MsgAndSeder(value, sender));
        super.tell(value, sender);
    }

    @Override
    public void signal(Signal signal) {
        signals.add(signal);
        super.signal(signal);
    }

    public MockActorRef<T> expectNoMsg() {
        try {
            Assert.assertNull(queue.poll(pollTimeout, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public MockActorRef<T> expectReceive(T value) {
        return expectReceive(eq(value));
    }

    public MockActorRef<T> expectReceive(String message, T value) {
        return expectReceive(eq(message, value));
    }

    public MockActorRef<T> expectReceive(T value, ActorRef<?> sender) {
        return expectReceive(eq(value, sender));
    }

    public MockActorRef<T> expectReceive(String message, T value, ActorRef<?> sender) {
        return expectReceive(eq(message, value, sender));
    }

    public MockActorRef<T> expectReceive(MsgAssertion<T> assertion) {
        MsgAndSeder poll = take(queue, pollTimeout);
        assertion.testAssert(poll.msg, poll.sender);
        return this;
    }

    public <P extends T> MockActorRef<T> expectReceive(Class<P> clazz, MsgAssertion<P> assertion) {
        MsgAndSeder poll = take(queue, pollTimeout);
        if (!clazz.isInstance(poll.msg)) {
            throw new AssertionError("expect type is: " + clazz + ", but got: " + poll.getClass());
        }
        assertion.testAssert((P) poll.msg, poll.sender);
        return this;
    }

    public MockActorRef<T> expectSignal(Signal signal) {
        Signal poll = take(signals, pollTimeout);
        Assert.assertEquals(poll, signal);
        return this;
    }

    private class MsgAndSeder {
        private final T msg;
        private final ActorRef<?> sender;

        private MsgAndSeder(T msg, ActorRef<?> sender) {
            this.msg = msg;
            this.sender = sender;
        }
    }
}
