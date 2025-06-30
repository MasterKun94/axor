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
    private final BlockingQueue<MsgAndRef<T>> received = new LinkedBlockingQueue<>();

    private final BlockingQueue<MsgAndRef<Object>> sent = new LinkedBlockingQueue<>();

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

    /**
     * Processes a message sent to this actor reference.
     * The message is added to the received queue before being passed to the delegate.
     *
     * @param value The message to process
     * @param sender The sender of the message
     */
    @Override
    protected void tell0(T value, ActorRef<?> sender) {
        received.add(new MsgAndRef<>(value, sender));
        super.tell0(value, sender);
    }

    /**
     * Processes a message sent inline to this actor reference.
     * The message is added to the received queue before being passed to the delegate.
     *
     * @param value The message to process
     * @param sender The sender of the message
     */
    @Override
    protected void tellInline0(T value, ActorRef<?> sender) {
        received.add(new MsgAndRef<>(value, sender));
        super.tellInline0(value, sender);
    }

    /**
     * Processes a signal sent to this actor reference.
     * The signal is added to the signals queue before being passed to the delegate.
     *
     * @param signal The signal to process
     */
    @Override
    public void signal(Signal signal) {
        signals.add(signal);
        super.signal(signal);
    }

    /**
     * Processes a signal sent inline to this actor reference.
     * The signal is added to the signals queue before being passed to the delegate.
     *
     * @param signal The signal to process
     */
    @Override
    public void signalInline(Signal signal) {
        signals.add(signal);
        super.signalInline(signal);
    }

    /**
     * Notifies this actor reference that a message has been sent to another actor.
     * The message and receiver are added to the sent queue.
     *
     * @param msg The message that was sent
     * @param receiver The receiver of the message
     * @param <P> The type of the message
     */
    @Override
    protected <P> void notifySend(P msg, ActorRef<P> receiver) {
        sent.add(new MsgAndRef<>(msg, receiver));
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
            MsgAndRef<T> poll = received.poll(pollTimeout, TimeUnit.MILLISECONDS);
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
    public MsgAndRef<T> pollFromReceived() {
        try {
            return received.poll(pollTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Polls for a message and its receiver from this actor's sent queue with the configured timeout.
     *
     * @return A MsgAndRef containing the message and its receiver, or null if no message was sent within the timeout
     * @throws RuntimeException if the thread is interrupted while waiting
     */
    public MsgAndRef<Object> pollFromSent() {
        try {
            return sent.poll(pollTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Clears all messages and signals from this actor's queues.
     */
    public void clear() {
        received.clear();
        signals.clear();
    }

    /**
     * Asserts that no message is sent within the configured timeout.
     *
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if the thread is interrupted while waiting
     * @throws AssertionError if a message is sent
     */
    public MockActorRef<T> expectNoMsgSend() {
        try {
            Assert.assertNull(sent.poll(pollTimeout, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * Asserts that a message equal to the expected value is sent.
     *
     * @param value The expected message value
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is sent within the timeout or the thread is interrupted
     * @throws AssertionError if the sent message does not match the expected value
     */
    public MockActorRef<T> expectSend(Object value) {
        return expectSend(MsgAssertions.msgEq(value));
    }

    /**
     * Asserts that a message equal to the expected value is sent, with a custom error message.
     *
     * @param message The error message to use if the assertion fails
     * @param value The expected message value
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is sent within the timeout or the thread is interrupted
     * @throws AssertionError if the sent message does not match the expected value
     */
    public MockActorRef<T> expectSend(String message, Object value) {
        return expectSend(MsgAssertions.msgEq(message, value));
    }

    /**
     * Asserts that a message equal to the expected value and to the expected receiver is sent.
     *
     * @param value The expected message value
     * @param receiver The expected receiver
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is sent within the timeout or the thread is interrupted
     * @throws AssertionError if the sent message or receiver does not match the expected values
     */
    public MockActorRef<T> expectSend(Object value, ActorRef<?> receiver) {
        return expectSend(eq(value, receiver));
    }

    /**
     * Asserts that a message equal to the expected value and to the expected receiver is sent,
     * with a custom error message.
     *
     * @param message The error message to use if the assertion fails
     * @param value The expected message value
     * @param receiver The expected receiver
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is sent within the timeout or the thread is interrupted
     * @throws AssertionError if the sent message or receiver does not match the expected values
     */
    public MockActorRef<T> expectSend(String message, Object value, ActorRef<?> receiver) {
        return expectSend(eq(message, value, receiver));
    }

    /**
     * Asserts that a sent message satisfies the given assertion.
     *
     * @param assertion The assertion to apply to the sent message and receiver
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is sent within the timeout or the thread is interrupted
     * @throws AssertionError if the assertion fails
     */
    public MockActorRef<T> expectSend(MsgAssertion<Object> assertion) {
        MsgAndRef<Object> poll = take(sent, pollTimeout);
        assertion.testAssert(poll.msg, poll.ref);
        return this;
    }

    /**
     * Asserts that a sent message is of the specified type and satisfies the given assertion.
     *
     * @param clazz The expected message class
     * @param assertion The assertion to apply to the sent message and receiver
     * @param <P> The expected message type
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if no message is sent within the timeout or the thread is interrupted
     * @throws AssertionError if the sent message is not of the expected type or the assertion fails
     */
    public <P> MockActorRef<T> expectSend(Class<P> clazz, MsgAssertion<P> assertion) {
        MsgAndRef<Object> poll = take(sent, pollTimeout);
        if (!clazz.isInstance(poll.msg)) {
            throw new AssertionError("expect type is: " + clazz + ", but got: " + poll.msg.getClass());
        }
        assertion.testAssert((P) poll.msg, poll.ref);
        return this;
    }


    /**
     * Asserts that no message is received within the configured timeout.
     *
     * @return This mock actor reference for method chaining
     * @throws RuntimeException if the thread is interrupted while waiting
     * @throws AssertionError if a message is received
     */
    public MockActorRef<T> expectNoMsgReceive() {
        try {
            Assert.assertNull(received.poll(pollTimeout, TimeUnit.MILLISECONDS));
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
        MsgAndRef<T> poll = take(received, pollTimeout);
        assertion.testAssert(poll.msg, poll.ref);
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
        MsgAndRef<T> poll = take(received, pollTimeout);
        if (!clazz.isInstance(poll.msg)) {
            throw new AssertionError("expect type is: " + clazz + ", but got: " + poll.msg.getClass());
        }
        assertion.testAssert((P) poll.msg, poll.ref);
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
    public static class MsgAndRef<T> {
        /** The received message */
        private final T msg;

        /** The sender of the message */
        private final ActorRef<?> ref;

        /**
         * Creates a new MsgAndRef.
         *
         * @param msg The received message
         * @param ref The target of the message
         */
        private MsgAndRef(T msg, ActorRef<?> ref) {
            this.msg = msg;
            this.ref = ref;
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
        public ActorRef<?> getRef() {
            return ref;
        }
    }
}
