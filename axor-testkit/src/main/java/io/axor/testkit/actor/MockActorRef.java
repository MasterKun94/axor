package io.axor.testkit.actor;

import io.axor.api.ActorRef;
import io.axor.api.ActorRefRich;
import io.axor.api.SystemEvent;
import io.axor.api.impl.ForwardingActorRef;
import io.axor.runtime.Signal;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamManager;
import org.junit.Assert;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.axor.testkit.actor.MsgAssertions.eq;

/**
 * A mock implementation of ActorRef for testing purposes.
 *
 * This class captures messages and signals sent to it, allowing tests to verify
 * that the expected messages were received. It also provides methods for asserting
 * on received messages and their senders.
 *
 * @param <T> The type of messages this actor reference can receive
 */
public class MockActorRef<T> extends ForwardingActorRef<T> {
    /** Queue for storing received messages and their senders */
    private final BlockingQueue<MsgAndSender> queue = new LinkedBlockingQueue<>();

    /** Queue for storing received signals */
    private final BlockingQueue<Signal> signals = new LinkedBlockingQueue<>();

    /** Timeout in milliseconds for polling operations */
    private long pollTimeout;

    /** Combined actor reference for delegation */
    private ActorRefRich<T> combine;

    /**
     * Creates a new MockActorRef that wraps the given delegate.
     *
     * @param delegate The actor reference to delegate to
     * @param pollTimeout The timeout in milliseconds for polling operations
     */
    public MockActorRef(ActorRef<T> delegate, long pollTimeout) {
        super(delegate);
        this.pollTimeout = pollTimeout;
    }

    /**
     * Takes an element from the queue with a timeout.
     *
     * @param queue The queue to take from
     * @param timeout The timeout in milliseconds
     * @param <T> The type of elements in the queue
     * @return The element taken from the queue
     * @throws RuntimeException if the timeout is reached or the thread is interrupted
     */
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

    /**
     * Sets the timeout for polling operations.
     *
     * @param timeout The new timeout
     * @throws IllegalArgumentException if the timeout is not positive
     */
    public void setTimeout(Duration timeout) {
        if (!timeout.isPositive()) {
            throw new IllegalArgumentException("timeout is not positive");
        }
        pollTimeout = timeout.toMillis();
    }
    @Override
    public void tell(T value, ActorRef<?> sender) {
        queue.add(new MsgAndSender(value, sender));
        super.tell(value, sender);
    }

    /**
     * Sends a message to this actor without a sender.
     * The message is captured for later verification.
     *
     * @param value The message to send
     */
    @Override
    public void tell(T value) {
        queue.add(new MsgAndSender(value, ActorRef.noSender()));
        super.tell(value);
    }

    /**
     * Sends a message to this actor with the specified sender, bypassing the mailbox.
     * The message is captured for later verification.
     *
     * @param value The message to send
     * @param sender The sender of the message
     */
    @Override
    public void tellInline(T value, ActorRef<?> sender) {
        queue.add(new MsgAndSender(value, sender));
        super.tellInline(value, sender);
    }

    /**
     * Sends a signal to this actor.
     * The signal is captured for later verification.
     *
     * @param signal The signal to send
     */
    @Override
    public void signal(Signal signal) {
        signals.add(signal);
        super.signal(signal);
    }

    /**
     * Sends a signal to this actor, bypassing the mailbox.
     * The signal is captured for later verification.
     *
     * @param signal The signal to send
     */
    @Override
    public void signalInline(Signal signal) {
        signals.add(signal);
        super.signalInline(signal);
    }

    /**
     * Combines this mock actor reference with a real actor reference.
     * This allows the mock to intercept messages while still delegating to a real actor.
     *
     * @param combine The actor reference to combine with
     * @throws IllegalArgumentException if the addresses don't match, the delegate is not a NoopActorRef,
     *                                  or this mock is already combined
     */
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

    /**
     * Adds a watcher to this actor reference.
     * If this mock is combined with a real actor reference, the watcher is added to the real actor.
     *
     * @param watcher The actor reference to add as a watcher
     * @param watchEvents The events to watch for
     */
    @Override
    public void addWatcher(ActorRef<?> watcher, List<Class<? extends SystemEvent>> watchEvents) {
        if (combine != null) {
            combine.addWatcher(watcher, watchEvents);
        } else {
            super.addWatcher(watcher, watchEvents);
        }
    }

    /**
     * Removes a watcher from this actor reference.
     * If this mock is combined with a real actor reference, the watcher is removed from the real actor.
     *
     * @param watcher The actor reference to remove as a watcher
     */
    @Override
    public void removeWatcher(ActorRef<?> watcher) {
        if (combine != null) {
            combine.removeWatcher(watcher);
        } else {
            super.removeWatcher(watcher);
        }
    }

    /**
     * Gets the stream definition for this actor reference.
     * If this mock is combined with a real actor reference, the definition is obtained from the real actor.
     *
     * @return The stream definition
     */
    @Override
    public StreamDefinition<T> getDefinition() {
        return combine != null ? combine.getDefinition() : super.getDefinition();
    }

    /**
     * Gets the stream manager for this actor reference.
     * If this mock is combined with a real actor reference, the manager is obtained from the real actor.
     *
     * @return The stream manager
     */
    @Override
    public StreamManager<T> getStreamManager() {
        return combine != null ? combine.getStreamManager() : super.getStreamManager();
    }

    /**
     * Polls for a message from this actor's queue with the configured timeout.
     *
     * @return The message, or null if no message was received within the timeout
     * @throws RuntimeException if the thread is interrupted while waiting
     */
    public T pollMessage() {
        try {
            MsgAndSender poll = queue.poll(pollTimeout, TimeUnit.MILLISECONDS);
            return poll == null ? null : poll.msg;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Polls for a message and its sender from this actor's queue with the configured timeout.
     *
     * @return A MsgAndSender containing the message and its sender, or null if no message was received within the timeout
     * @throws RuntimeException if the thread is interrupted while waiting
     */
    public MsgAndSender poll() {
        try {
            return queue.poll(pollTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Clears all messages and signals from this actor's queues.
     */
    public void clear() {
        queue.clear();
        signals.clear();
    }

    /**
     * Asserts that no message is received within the configured timeout.
     *
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if the thread is interrupted while waiting
     * @throws AssertionError if a message is received
     */
    public MockActorRef<T> expectNoMsg() {
        try {
            Assert.assertNull(queue.poll(pollTimeout, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * Asserts that a message equal to the expected value is received.
     *
     * @param value The expected message value
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is received within the timeout or the thread is interrupted
     * @throws AssertionError if the received message does not match the expected value
     */
    public MockActorRef<T> expectReceive(T value) {
        return expectReceive(MsgAssertions.msgEq(value));
    }

    /**
     * Asserts that a message equal to the expected value is received, with a custom error message.
     *
     * @param message The error message to use if the assertion fails
     * @param value The expected message value
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is received within the timeout or the thread is interrupted
     * @throws AssertionError if the received message does not match the expected value
     */
    public MockActorRef<T> expectReceive(String message, T value) {
        return expectReceive(MsgAssertions.msgEq(message, value));
    }

    /**
     * Asserts that a message equal to the expected value and from the expected sender is received.
     *
     * @param value The expected message value
     * @param sender The expected sender
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is received within the timeout or the thread is interrupted
     * @throws AssertionError if the received message or sender does not match the expected values
     */
    public MockActorRef<T> expectReceive(T value, ActorRef<?> sender) {
        return expectReceive(eq(value, sender));
    }

    /**
     * Asserts that a message equal to the expected value and from the expected sender is received,
     * with a custom error message.
     *
     * @param message The error message to use if the assertion fails
     * @param value The expected message value
     * @param sender The expected sender
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is received within the timeout or the thread is interrupted
     * @throws AssertionError if the received message or sender does not match the expected values
     */
    public MockActorRef<T> expectReceive(String message, T value, ActorRef<?> sender) {
        return expectReceive(eq(message, value, sender));
    }

    /**
     * Asserts that a received message satisfies the given assertion.
     *
     * @param assertion The assertion to apply to the received message and sender
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is received within the timeout or the thread is interrupted
     * @throws AssertionError if the assertion fails
     */
    public MockActorRef<T> expectReceive(MsgAssertion<T> assertion) {
        MsgAndSender poll = take(queue, pollTimeout);
        assertion.testAssert(poll.msg, poll.sender);
        return this;
    }

    /**
     * Asserts that a received message is of the specified type and satisfies the given assertion.
     *
     * @param clazz The expected message class
     * @param assertion The assertion to apply to the received message and sender
     * @param <P> The expected message type, which must be a subtype of T
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is received within the timeout or the thread is interrupted
     * @throws AssertionError if the received message is not of the expected type or the assertion fails
     */
    public <P extends T> MockActorRef<T> expectReceive(Class<P> clazz, MsgAssertion<P> assertion) {
        MsgAndSender poll = take(queue, pollTimeout);
        if (!clazz.isInstance(poll.msg)) {
            throw new AssertionError("expect type is: " + clazz + ", but got: " + poll.msg.getClass());
        }
        assertion.testAssert((P) poll.msg, poll.sender);
        return this;
    }

    /**
     * Asserts that a signal equal to the expected signal is received.
     *
     * @param signal The expected signal
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no signal is received within the timeout or the thread is interrupted
     * @throws AssertionError if the received signal does not match the expected signal
     */
    public MockActorRef<T> expectSignal(Signal signal) {
        Signal poll = take(signals, pollTimeout);
        Assert.assertEquals(poll, signal);
        return this;
    }

    /**
     * Returns a hash code value for this actor reference.
     * If this mock is combined with a real actor reference, the hash code is obtained from the real actor.
     *
     * @return A hash code value for this actor reference
     */
    @Override
    public int hashCode() {
        return combine != null ? combine.hashCode() : super.hashCode();
    }

    /**
     * Compares this actor reference to the specified object.
     * If this mock is combined with a real actor reference and the object is not a MockActorRef,
     * the comparison is delegated to the real actor.
     *
     * @param obj The object to compare with
     * @return true if the objects are equal, false otherwise
     */
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

    /**
     * A container class for a message and its sender.
     * This class is used to store messages received by the mock actor reference.
     */
    public class MsgAndSender {
        /** The received message */
        private final T msg;

        /** The sender of the message */
        private final ActorRef<?> sender;

        /**
         * Creates a new MsgAndSender.
         *
         * @param msg The received message
         * @param sender The sender of the message
         */
        private MsgAndSender(T msg, ActorRef<?> sender) {
            this.msg = msg;
            this.sender = sender;
        }

        /**
         * Gets the received message.
         *
         * @return The message
         */
        public T getMsg() {
            return msg;
        }

        /**
         * Gets the sender of the message.
         *
         * @return The sender
         */
        public ActorRef<?> getSender() {
            return sender;
        }
    }
}
