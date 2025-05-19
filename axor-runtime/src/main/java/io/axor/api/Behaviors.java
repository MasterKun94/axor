package io.axor.api;

import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides a set of static methods to create and manage actor behaviors. Behaviors are the core
 * components that define how actors process messages and can be used to implement various stateful
 * and stateless message handling strategies.
 *
 * <p>This class includes methods for creating predefined behaviors such as stopping an actor,
 * indicating that a message is unhandled, or keeping the current behavior. It also provides methods
 * to define custom behaviors using functional interfaces.
 */
public class Behaviors {

    /**
     * Returns a behavior that keeps the current behavior of an actor, effectively doing nothing and
     * allowing the actor to continue with its current state.
     *
     * @param <T> the type of messages this actor can handle
     * @return a {@code Behavior<T>} that represents the same behavior as the current one
     */
    @SuppressWarnings("unchecked")
    public static <T> Behavior<T> same() {
        return new InternalBehavior<>(BehaviorType.SAME);
    }

    /**
     * Returns a behavior that stops the actor, causing it to terminate and no longer process any
     * messages.
     *
     * @param <T> the type of messages this actor can handle
     * @return a {@code Behavior<T>} that represents the stop behavior, which will terminate the
     * actor
     */
    @SuppressWarnings("unchecked")
    public static <T> Behavior<T> stop() {
        return new InternalBehavior<>(BehaviorType.STOP);
    }

    /**
     * Returns a behavior that indicates the message is unhandled by the actor. This behavior can be
     * used to signify that the actor does not have a specific handler for the received message.
     *
     * @param <T> the type of messages this actor can handle
     * @return a {@code Behavior<T>} that represents the unhandled behavior, indicating the message
     * is not processed
     */
    public static <T> Behavior<T> unhandled() {
        return new InternalBehavior<>(BehaviorType.UNHANDLED);
    }

    /**
     * Creates a behavior that processes a message using the provided handler.
     *
     * <p>This method takes a {@code BiFunction} that defines how an actor should handle a message.
     * The function is applied to the current {@code ActorContext} and the received message, and it
     * returns a new or updated behavior for the actor.
     *
     * @param <T>     the type of messages this actor can handle
     * @param handler a {@code BiFunction} that takes the current {@code ActorContext<T>} and the
     *                received message, and returns a new or updated {@code Behavior<T>}
     * @return a {@code Behavior<T>} that represents the behavior defined by the provided handler
     */
    public static <T> Behavior<T> receiveMessage(BiFunction<ActorContext<T>, T, Behavior<T>> handler) {
        return handler::apply;
    }

    /**
     * Creates a behavior that processes a message using the provided handler.
     *
     * <p>This method takes a {@code Function} that defines how an actor should handle a message.
     * The function is applied to the received message, and it returns a new or updated behavior for
     * the actor.
     *
     * @param <T>     the type of messages this actor can handle
     * @param handler a {@code Function} that takes the received message and returns a new or
     *                updated {@code Behavior<T>}
     * @return a {@code Behavior<T>} that represents the behavior defined by the provided handler
     */
    public static <T> Behavior<T> receiveMessage(Function<T, Behavior<T>> handler) {
        return (ctx, msg) -> handler.apply(msg);
    }

    /**
     * Creates a behavior that processes both messages and signals using the provided handlers.
     *
     * <p>This method takes two {@code BiFunction}s: one for handling messages and another for
     * handling signals. The message handler is applied to the current {@code ActorContext} and the
     * received message, and it returns a new or updated behavior for the actor. The signal handler
     * is applied to the current {@code ActorContext} and the received signal, and it also returns a
     * new or updated behavior for the actor.
     *
     * @param <T>           the type of messages this actor can handle
     * @param msgHandler    a {@code BiFunction} that takes the current {@code ActorContext<T>} and
     *                      the received message, and returns a new or updated {@code Behavior<T>}
     * @param signalHandler a {@code BiFunction} that takes the current {@code ActorContext<T>} and
     *                      the received signal, and returns a new or updated {@code Behavior<T>}
     * @return a {@code Behavior<T>} that represents the behavior defined by the provided message
     * and signal handlers
     */
    public static <T> Behavior<T> receive(BiFunction<ActorContext<T>, T, Behavior<T>> msgHandler,
                                          BiFunction<ActorContext<T>, Signal, Behavior<T>> signalHandler) {
        return new Behavior<>() {
            @Override
            public Behavior<T> onReceive(ActorContext<T> context, T message) {
                return msgHandler.apply(context, message);
            }

            @Override
            public Behavior<T> onSignal(ActorContext<T> context, Signal signal) {
                return signalHandler.apply(context, signal);
            }
        };
    }

    /**
     * Creates a behavior that processes messages and signals using the provided handlers.
     *
     * <p>This method takes two functions: one for handling messages and another for handling
     * signals. The message handler is applied to the received message, and it returns a new or
     * updated behavior for the actor. The signal handler is applied to the received signal, and it
     * also returns a new or updated behavior for the actor.
     *
     * @param <T>           the type of messages this actor can handle
     * @param msgHandler    a {@code Function} that takes the received message and returns a new or
     *                      updated {@code Behavior<T>}
     * @param signalHandler a {@code Function} that takes the received signal and returns a new or
     *                      updated {@code Behavior<T>}
     * @return a {@code Behavior<T>} that represents the behavior defined by the provided message
     * and signal handlers
     */
    public static <T> Behavior<T> receive(Function<T, Behavior<T>> msgHandler, Function<Signal,
            Behavior<T>> signalHandler) {
        return new Behavior<>() {
            @Override
            public Behavior<T> onReceive(ActorContext<T> context, T message) {
                return msgHandler.apply(message);
            }

            @Override
            public Behavior<T> onSignal(ActorContext<T> context, Signal signal) {
                return signalHandler.apply(signal);
            }
        };
    }

    /**
     * Wraps the given behavior with logging functionality. This method creates a new behavior that
     * logs each message and signal received by the actor before delegating to the original
     * behavior.
     *
     * @param <T>      the type of messages this actor can handle
     * @param behavior the original behavior to wrap with logging
     * @param log      the logger to use for logging
     * @param level    the logging level at which to log the messages and signals
     * @return a new {@code Behavior<T>} that logs messages and signals before delegating to the
     * original behavior
     */
    public static <T> Behavior<T> log(Behavior<T> behavior, Logger log, Level level) {
        return new Behavior<>() {
            @Override
            public Behavior<T> onReceive(ActorContext<T> context, T message) {
                log.atLevel(level).log("Receive message: [{}] from sender: [{}]",
                        MessageUtils.loggable(message), context.sender());
                return behavior.onReceive(context, message);
            }

            @Override
            public Behavior<T> onSignal(ActorContext<T> context, Signal signal) {
                log.atLevel(level).log("Receive signal: [{}]", signal);
                return behavior.onSignal(context, signal);
            }
        };
    }

    /**
     * Creates a composite behavior that combines two behaviors: one for handling messages and
     * another for handling signals.
     *
     * <p>If either of the provided behaviors is already a {@code CompositeBehavior}, it will be
     * unwrapped, and only the inner message or signal behavior will be used. If any of the provided
     * behaviors is an instance of {@code InternalBehavior} other than {@code SAME}, an
     * {@code UnsupportedOperationException} will be thrown.
     *
     * @param <T>            the type of messages this actor can handle
     * @param msgBehavior    the behavior to use for handling messages
     * @param signalBehavior the behavior to use for handling signals
     * @return a new {@code Behavior<T>} that represents the composite behavior
     * @throws UnsupportedOperationException if either of the provided behaviors is an unsupported
     *                                       {@code InternalBehavior}
     */
    public static <T> Behavior<T> composite(Behavior<T> msgBehavior, Behavior<T> signalBehavior) {
        if (msgBehavior instanceof CompositeBehavior<T> c) {
            return composite(c.msgBehavior, signalBehavior);
        }
        if (signalBehavior instanceof CompositeBehavior<T> c) {
            return composite(msgBehavior, c.signalBehavior);
        }
        if (msgBehavior instanceof InternalBehavior<T>(var type) && type != BehaviorType.SAME) {
            throw new UnsupportedOperationException("CompositeBehavior does not support " +
                                                    msgBehavior);
        }
        if (signalBehavior instanceof InternalBehavior<T>(var type) && type != BehaviorType.SAME) {
            throw new UnsupportedOperationException("CompositeBehavior does not support " +
                                                    signalBehavior);
        }
        return new CompositeBehavior<>(msgBehavior, signalBehavior);
    }

    /**
     * Creates a new instance of {@code ConsumeBuffer} with the specified starting behavior.
     *
     * <p>The {@code ConsumeBuffer} is used to buffer messages and signals, which can later be
     * converted into a behavior that will process these buffered items.
     *
     * @param <T>           the type of messages this actor can handle
     * @param startBehavior the initial behavior for the actor before any messages or signals are
     *                      added
     * @return a new instance of {@code ConsumeBuffer<T>} initialized with the given starting
     * behavior
     */
    public static <T> ConsumeBuffer<T> consumeBuffer(Behavior<T> startBehavior) {
        return new ConsumeBuffer<>(() -> startBehavior);
    }

    public static <T> ConsumeBuffer<T> consumeBuffer(Supplier<Behavior<T>> behaviorSupplier) {
        return new ConsumeBuffer<>(behaviorSupplier);
    }

    sealed interface MsgOrSignal<T> {
    }

    interface SpecialBehavior<T> extends Behavior<T> {
        @Override
        default Behavior<T> onReceive(ActorContext<T> context, T message) {
            throw new UnsupportedOperationException();
        }

        @Override
        default Behavior<T> onSignal(ActorContext<T> context, Signal signal) {
            throw new UnsupportedOperationException();
        }

        BehaviorType type();
    }

    record InternalBehavior<T>(BehaviorType type) implements SpecialBehavior<T> {
    }

    record CompositeBehavior<T>(Behavior<T> msgBehavior,
                                Behavior<T> signalBehavior) implements Behavior<T> {

        @Override
        public Behavior<T> onReceive(ActorContext<T> context, T message) {
            return msgBehavior.onReceive(context, message);
        }

        @Override
        public Behavior<T> onSignal(ActorContext<T> context, Signal signal) {
            return signalBehavior.onSignal(context, signal);
        }
    }

    /**
     * A utility class for constructing a buffer of messages and signals to be consumed by an actor.
     * This class allows you to add messages and signals to a buffer, which can then be converted
     * into a behavior that will process these buffered items.
     *
     * @param <T> the type of messages this actor can handle
     */
    public static class ConsumeBuffer<T> {
        private final Supplier<Behavior<T>> supplier;
        private final List<MsgOrSignal<T>> buffers = new ArrayList<>();

        public ConsumeBuffer(Supplier<Behavior<T>> supplier) {
            this.supplier = supplier;
        }

        public ConsumeBuffer<T> addMsg(T msg, ActorRef<?> sender) {
            buffers.add(new MsgHolder<>(msg, sender));
            return this;
        }

        public ConsumeBuffer<T> addSignal(Signal signal) {
            buffers.add(new SignalHolder<>(signal));
            return this;
        }

        public Behavior<T> toBehavior() {
            return new ConsumeBufferBehavior<>(supplier.get(), buffers);
        }
    }

    record ConsumeBufferBehavior<T>(Behavior<T> behavior,
                                    List<MsgOrSignal<T>> buffers) implements SpecialBehavior<T> {
        @Override
        public BehaviorType type() {
            return BehaviorType.CONSUME_BUFFER;
        }
    }

    record MsgHolder<T>(T msg, ActorRef<?> sender) implements MsgOrSignal<T> {
    }

    record SignalHolder<T>(Signal signal) implements MsgOrSignal<T> {
    }
}
