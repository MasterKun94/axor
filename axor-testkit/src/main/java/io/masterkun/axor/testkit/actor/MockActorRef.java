package io.masterkun.axor.testkit.actor;

import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.api.Signal;
import io.masterkun.axor.api.SystemEvent;
import io.masterkun.axor.api.impl.ForwardingActorRef;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamManager;
import org.junit.Assert;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.masterkun.axor.testkit.actor.MsgAssertions.eq;

public class MockActorRef<T> extends ForwardingActorRef<T> {
    private final BlockingQueue<MsgAndSender> queue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Signal> signals = new LinkedBlockingQueue<>();
    private final long pollTimeout;
    private ActorRefRich<T> combine;

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
        queue.add(new MsgAndSender(value, sender));
        super.tell(value, sender);
    }

    @Override
    public void tell(T value) {
        queue.add(new MsgAndSender(value, ActorRef.noSender()));
        super.tell(value);
    }

    @Override
    public void tellInline(T value, ActorRef<?> sender) {
        queue.add(new MsgAndSender(value, sender));
        super.tellInline(value, sender);
    }

    @Override
    public void signal(Signal signal) {
        signals.add(signal);
        super.signal(signal);
    }

    @Override
    public void signalInline(Signal signal) {
        signals.add(signal);
        super.signalInline(signal);
    }

    public void combineWith(ActorRef<T> combine) {
        if (!address().equals(combine.address())) {
            throw new IllegalArgumentException("not same address");
        }
        if (!(getDelegate() instanceof NoopActorRef<T>)) {
            throw new IllegalArgumentException("delegate should be noop");
        }
        if (this.combine != null) {
            throw new IllegalArgumentException("already combined");
        }
        this.combine = (ActorRefRich<T>) combine;
    }

    @Override
    public void addWatcher(ActorRef<?> watcher, List<Class<? extends SystemEvent>> watchEvents) {
        if (combine != null) {
            combine.addWatcher(watcher, watchEvents);
        } else {
            super.addWatcher(watcher, watchEvents);
        }
    }

    @Override
    public void removeWatcher(ActorRef<?> watcher) {
        if (combine != null) {
            combine.removeWatcher(watcher);
        } else {
            super.removeWatcher(watcher);
        }
    }

    @Override
    public StreamDefinition<T> getDefinition() {
        return combine != null ? combine.getDefinition() : super.getDefinition();
    }

    @Override
    public StreamManager<T> getStreamManager() {
        return combine != null ? combine.getStreamManager() : super.getStreamManager();
    }

    public T pollMessage() {
        try {
            MsgAndSender poll = queue.poll(pollTimeout, TimeUnit.MILLISECONDS);
            return poll == null ? null : poll.msg;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public MsgAndSender poll() {
        try {
            return queue.poll(pollTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void clear() {
        queue.clear();
        signals.clear();
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
        return expectReceive(MsgAssertions.msgEq(value));
    }

    public MockActorRef<T> expectReceive(String message, T value) {
        return expectReceive(MsgAssertions.msgEq(message, value));
    }

    public MockActorRef<T> expectReceive(T value, ActorRef<?> sender) {
        return expectReceive(eq(value, sender));
    }

    public MockActorRef<T> expectReceive(String message, T value, ActorRef<?> sender) {
        return expectReceive(eq(message, value, sender));
    }

    public MockActorRef<T> expectReceive(MsgAssertion<T> assertion) {
        MsgAndSender poll = take(queue, pollTimeout);
        assertion.testAssert(poll.msg, poll.sender);
        return this;
    }

    public <P extends T> MockActorRef<T> expectReceive(Class<P> clazz, MsgAssertion<P> assertion) {
        MsgAndSender poll = take(queue, pollTimeout);
        if (!clazz.isInstance(poll.msg)) {
            throw new AssertionError("expect type is: " + clazz + ", but got: " + poll.msg.getClass());
        }
        assertion.testAssert((P) poll.msg, poll.sender);
        return this;
    }

    public MockActorRef<T> expectSignal(Signal signal) {
        Signal poll = take(signals, pollTimeout);
        Assert.assertEquals(poll, signal);
        return this;
    }

    @Override
    public int hashCode() {
        return combine != null ? combine.hashCode() : super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MockActorRef<?>) {
            return super.equals(obj);
        }
        if (combine != null) {
            return combine.equals(obj);
        }
        return super.equals(obj);
    }

    public class MsgAndSender {
        private final T msg;
        private final ActorRef<?> sender;

        private MsgAndSender(T msg, ActorRef<?> sender) {
            this.msg = msg;
            this.sender = sender;
        }

        public T getMsg() {
            return msg;
        }

        public ActorRef<?> getSender() {
            return sender;
        }
    }
}
