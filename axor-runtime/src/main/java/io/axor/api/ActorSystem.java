package io.axor.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.api.impl.ActorSystemImpl;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.task.DependencyTaskRegistry;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.EventDispatcherGroup;
import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamServer;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Stops all actors within the actor system and shuts down the system itself. This method ensures
 * that all resources are released and all actors are properly terminated before the system is fully
 * shut down.
 *
 * @return an {@code EventStage<Void>} that can be used to track the completion of the shutdown
 * process
 */
public interface ActorSystem {
    static ActorSystem create(String name) {
        return create(name, ConfigFactory.load().resolve());
    }

    static ActorSystem create(String name, Config config) {
        return create(name, config, (StreamServer) null);
    }

    static ActorSystem create(String name,
                              Config config,
                              StreamServer streamServer) {
        return create(name, config, streamServer, null);
    }

    static ActorSystem create(String name,
                              Config config,
                              EventDispatcherGroup eventExecutorGroup) {
        return create(name, config, null, eventExecutorGroup);
    }

    static ActorSystem create(String name,
                              Config config,
                              StreamServer streamServer,
                              EventDispatcherGroup eventExecutorGroup) {
        return create(name, streamServer, eventExecutorGroup, config);
    }

    static ActorSystem create(String name,
                              StreamServer streamServer,
                              EventDispatcherGroup eventExecutorGroup,
                              Config config) {
        return new ActorSystemImpl(name, streamServer, eventExecutorGroup, config);
    }

    /**
     * Checks if there are multiple instances of the ActorSystem running within the same JVM.
     *
     * @return true if there are multiple instances of the ActorSystem in the JVM, false otherwise.
     */
    static boolean hasMultiInstance() {
        return ActorSystemImpl.hasMultipalInstance();
    }

    /**
     * Returns the name of the actor system.
     *
     * @return the name of the actor system as a {@code String}
     */
    String name();

    /**
     * Returns the configuration used to create this actor system.
     *
     * @return the {@code Config} object containing the configuration settings for this actor system
     */
    Config config();

    /**
     * Creates an {@code ActorAddress} for the given actor name.
     *
     * @param name the name of the actor for which to create the address
     * @return an {@code ActorAddress} representing the address of the specified actor
     */
    default ActorAddress address(String name) {
        return ActorAddress.create(name(), publishAddress(), name);
    }

    /**
     * Returns the address at which this actor system is published.
     *
     * @return the {@code Address} representing the host and port where the actor system is
     * accessible
     */
    Address publishAddress();

    /**
     * Returns the logger associated with this actor system, which can be used for logging messages
     * and events.
     *
     * @return the {@code Logger} instance for this actor system
     */
    Logger getLogger();

    /**
     * Determines whether the specified actor is local to the current actor system.
     *
     * @param actor the {@code ActorRef} to check
     * @return {@code true} if the actor is local, {@code false} otherwise
     */
    boolean isLocal(ActorRef<?> actor);

    /**
     * Reports a system failure, indicating that an unexpected error or exception has occurred
     * within the actor system. This method is typically called when a critical issue arises that
     * cannot be handled by individual actors and requires immediate attention.
     *
     * @param cause the Throwable that represents the cause of the system failure
     */
    void systemFailure(Throwable cause);

    /**
     * Returns an {@code EventStream} that publishes {@code DeadLetter} events. A {@code DeadLetter}
     * event is emitted when a message cannot be delivered to its intended recipient, typically
     * because the recipient actor does not exist, has been terminated, or is otherwise unable to
     * receive the message.
     *
     * @return an {@code EventStream<DeadLetter>} that can be used to subscribe to and handle dead
     * letter events
     */
    Eventbus<DeadLetter> deadLetters();

    /**
     * Returns an {@code EventStream} that publishes system events. System events are used to
     * provide information about the lifecycle and operational status of actors and streams within
     * the actor system. These events can be useful for monitoring, debugging, and reacting to
     * changes in the system.
     *
     * @return an {@code EventStream<SystemEvent>} that can be used to subscribe to and handle
     * system events
     */
    Eventbus<SystemEvent> systemEvents();

    /**
     * Starts a new actor with the specified creator and name.
     *
     * @param <T>     the type of messages that the actor can handle
     * @param creator the {@code ActorCreator<T>} that is responsible for creating the actor
     *                instance
     * @param name    the name of the actor, which must be unique within the actor system
     * @return an {@code ActorRef<T>} representing the reference to the newly created actor
     */
    default <T> ActorRef<T> start(ActorCreator<T> creator, String name) {
        return start(creator, name, getDispatcherGroup().nextDispatcher());
    }

    /**
     * Starts a new actor with the specified creator, name, and event dispatcher.
     *
     * @param <T>        the type of messages that the actor can handle
     * @param creator    the {@code ActorCreator<T>} that is responsible for creating the actor
     *                   instance
     * @param name       the name of the actor, which must be unique within the actor system
     * @param dispatcher the {@code EventDispatcher} to be used for dispatching events to the actor
     * @return an {@code ActorRef<T>} representing the reference to the newly created actor
     */
    <T> ActorRef<T> start(ActorCreator<T> creator, String name, EventDispatcher dispatcher);

    /**
     * Retrieves an existing actor by name or starts a new one if it does not exist.
     *
     * @param <T>     the type of messages that the actor can handle
     * @param creator the actor creator used to start a new actor if one does not already exist
     * @param name    the name of the actor to retrieve or start
     * @return the actor reference for the existing or newly started actor
     */
    default <T> ActorRef<T> getOrStart(ActorCreator<T> creator, String name) {
        return getOrStart(creator, name, getDispatcherGroup().nextDispatcher());
    }

    /**
     * Retrieves an existing actor by name or starts a new one if it does not exist.
     *
     * @param <T>        the type of messages this actor can handle
     * @param creator    the function that creates and configures the actor
     * @param name       the unique name of the actor
     * @param dispatcher the event dispatcher to be used for processing the actor's messages
     * @return an ActorRef representing the existing or newly started actor
     */
    <T> ActorRef<T> getOrStart(ActorCreator<T> creator, String name, EventDispatcher dispatcher);

    /**
     * Stops the specified actor.
     *
     * @param actor the {@code ActorRef} of the actor to be stopped
     * @return an {@code EventStage<Void>} that can be used to track the completion of the stop
     * operation
     */
    EventStage<Void> stop(ActorRef<?> actor);

    /**
     * Retrieves the {@code ActorRef} for the actor located at the specified address.
     *
     * @param address the {@code ActorAddress} of the actor to retrieve
     * @return the {@code ActorRef<?>} representing the reference to the actor
     * @throws ActorNotFoundException if no actor is found at the specified address
     */
    ActorRef<?> get(ActorAddress address) throws ActorNotFoundException;


    /**
     * Retrieves the {@code ActorRef} for the actor located at the specified address, ensuring that
     * the actor can handle messages of the given type.
     *
     * @param <T>     the type of messages that the actor can handle
     * @param address the {@code ActorAddress} of the actor to retrieve
     * @param msgType the {@code MsgType<T>} representing the type of messages that the actor should
     *                be able to handle
     * @return an {@code ActorRef<T>} representing the reference to the actor
     * @throws ActorNotFoundException  if no actor is found at the specified address
     * @throws IllegalMsgTypeException if the actor at the specified address cannot handle messages
     *                                 of the given type
     */
    <T> ActorRef<T> get(ActorAddress address, MsgType<T> msgType) throws ActorNotFoundException,
            IllegalMsgTypeException;

    /**
     * Retrieves the {@code ActorRef} for the actor located at the specified address, ensuring that
     * the actor can handle messages of the given type.
     *
     * @param <T>     the type of messages that the actor can handle
     * @param address the {@code ActorAddress} of the actor to retrieve
     * @param type    the {@code Class<T>} representing the type of messages that the actor should
     *                be able to handle
     * @return an {@code ActorRef<T>} representing the reference to the actor
     * @throws ActorNotFoundException  if no actor is found at the specified address
     * @throws IllegalMsgTypeException if the actor at the specified address cannot handle messages
     *                                 of the given type
     */
    default <T> ActorRef<T> get(ActorAddress address, Class<T> type) throws ActorNotFoundException, IllegalMsgTypeException {
        return get(address, MsgType.of(type));
    }

    /**
     * Provides an {@code ActorRef} that represents a "no sender" actor reference. This is typically
     * used when sending messages without specifying a sender, which means that the recipient will
     * not be able to reply to the message.
     *
     * @param <T> the type of messages that the actor can handle
     * @return an {@code ActorRef<T>} representing the "no sender" actor reference
     */
    <T> ActorRef<T> noSender();

    /**
     * Retrieves the SerdeRegistry associated with this actor system. The SerdeRegistry is used to
     * manage serializers and deserializers for various data types, which are essential for message
     * serialization and deserialization in distributed systems.
     *
     * @return the {@code SerdeRegistry} instance associated with this actor system
     */
    SerdeRegistry getSerdeRegistry();

    /**
     * Returns the {@code StreamServer} associated with this actor system. The {@code StreamServer}
     * is responsible for managing stream channels, handling message serialization, and providing
     * network communication capabilities.
     *
     * @return the {@code StreamServer} instance associated with this actor system
     */
    StreamServer getStreamServer();

    /**
     * Retrieves the {@code EventDispatcherGroup} associated with this actor system. The
     * {@code EventDispatcherGroup} is responsible for managing a collection of event dispatchers,
     * which are used to distribute and process events or messages within the actor system.
     *
     * @return the {@code EventDispatcherGroup} instance associated with this actor system
     */
    EventDispatcherGroup getDispatcherGroup();

    /**
     * Retrieves the scheduler associated with the given event dispatcher.
     *
     * @param dispatcher the event dispatcher for which the scheduler is retrieved
     * @return the scheduler linked to the provided event dispatcher
     */
    Scheduler getScheduler(EventDispatcher dispatcher);

    /**
     * Initiates the asynchronous shutdown of the actor system. This method will trigger the
     * termination process, which includes stopping all actors, closing network connections, and
     * releasing any other resources held by the system. The returned CompletableFuture will be
     * completed once the shutdown process is finished.
     *
     * @return a CompletableFuture that will be completed when the actor system has been fully shut
     * down
     */
    CompletableFuture<Void> shutdownAsync();

    /**
     * Returns a {@code DependencyTaskRegistry} that can be used to register tasks that should be
     * executed during the shutdown of the actor system. These tasks are typically used for cleanup,
     * resource release, or any other necessary operations that need to be performed when the system
     * is shutting down.
     *
     * @return a {@code DependencyTaskRegistry} for registering shutdown tasks
     */
    DependencyTaskRegistry shutdownHooks();
}
