package io.axor.testkit.actor;

import io.axor.api.Actor;
import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.api.impl.ActorUnsafe;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;

import java.time.Duration;

/**
 * A test kit for actor-based testing in the Axor framework.
 *
 * This class provides utilities for creating and managing mock actors during tests.
 * It allows for setting timeouts, creating mock actor references, and verifying actor interactions.
 */
public class ActorTestKit {
    /** Timeout in milliseconds for message expectations in mock actors */
    private long timeTimeMills;

    /**
     * Creates a new ActorTestKit with default timeout.
     */
    public ActorTestKit() {
    }

    /**
     * Creates a new ActorTestKit with the specified timeout.
     *
     * @param timeout The duration to wait for expected messages
     */
    public ActorTestKit(Duration timeout) {
        setMsgTimeout(timeout);
    }

    /**
     * Sets the timeout for message expectations.
     *
     * @param timeout The duration to wait for expected messages
     */
    public void setMsgTimeout(Duration timeout) {
        timeTimeMills = timeout.toMillis();
    }

    /**
     * Creates a mock actor reference with the specified address and message type.
     *
     * @param address The actor address
     * @param msgType The message type
     * @param <T> The type of messages the actor can receive
     * @return A mock actor reference
     */
    public <T> MockActorRef<T> mock(ActorAddress address, MsgType<T> msgType) {
        return mock(new NoopActorRef<>(address, msgType));
    }

    /**
     * Creates a mock actor reference with the specified address and message class.
     *
     * @param address The actor address
     * @param msgType The message class
     * @param <T> The type of messages the actor can receive
     * @return A mock actor reference
     */
    public <T> MockActorRef<T> mock(ActorAddress address, Class<T> msgType) {
        return mock(new NoopActorRef<>(address, MsgType.of(msgType)));
    }

    /**
     * Creates a mock actor reference that wraps an existing actor reference.
     *
     * @param ref The actor reference to wrap
     * @param <T> The type of messages the actor can receive
     * @return A mock actor reference
     */
    public <T> MockActorRef<T> mock(ActorRef<T> ref) {
        return new MockActorRef<>(ref, timeTimeMills);
    }

    /**
     * Creates a mock actor in the specified actor system with the given name and message type.
     * This method starts a real actor in the system that forwards messages to the mock.
     *
     * @param name The name of the actor
     * @param msgType The message type
     * @param system The actor system
     * @param <T> The type of messages the actor can receive
     * @return A mock actor reference
     */
    public <T> MockActorRef<T> mock(String name, MsgType<T> msgType, ActorSystem system) {
        MockActorRef<T> mock = mock(system.address(name), msgType);
        ActorRef<T> start = system.start(c -> new MockActor<>(c, msgType, mock), name);
        mock.combineWith(start);
        ActorUnsafe.replaceCache(system, mock);
        return mock;
    }

    /**
     * Creates a mock actor in the specified actor system with the given name and message class.
     * This method starts a real actor in the system that forwards messages to the mock.
     *
     * @param name The name of the actor
     * @param msgType The message class
     * @param system The actor system
     * @param <T> The type of messages the actor can receive
     * @return A mock actor reference
     */
    public <T> MockActorRef<T> mock(String name, Class<T> msgType, ActorSystem system) {
        return mock(name, MsgType.of(msgType), system);
    }

    /**
     * A mock actor implementation that forwards all messages and signals to a MockActorRef.
     * This class is used internally by the ActorTestKit to create mock actors in an actor system.
     *
     * @param <T> The type of messages the actor can receive
     */
    private static class MockActor<T> extends Actor<T> {
        /** The message type this actor handles */
        private final MsgType<T> msgType;

        /** The mock actor reference that will receive forwarded messages */
        private final MockActorRef<T> mock;

        /**
         * Creates a new MockActor.
         *
         * @param context The actor context
         * @param msgType The message type
         * @param mock The mock actor reference to forward messages to
         */
        protected MockActor(ActorContext<T> context, MsgType<T> msgType, MockActorRef<T> mock) {
            super(context);
            this.msgType = msgType;
            this.mock = mock;
        }

        /**
         * Forwards received messages to the mock actor reference.
         *
         * @param t The received message
         */
        @Override
        public void onReceive(T t) {
            mock.tell(t, sender());
        }

        /**
         * Forwards received signals to the mock actor reference.
         *
         * @param signal The received signal
         */
        @Override
        public void onSignal(Signal signal) {
            mock.signal(signal);
        }

        /**
         * Returns the message type this actor handles.
         *
         * @return The message type
         */
        @Override
        public MsgType<T> msgType() {
            return msgType;
        }
    }
}
