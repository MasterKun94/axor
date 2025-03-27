package io.masterkun.axor.api;

/**
 * Abstract base class for actors, providing a framework for defining the behavior and lifecycle of
 * an actor. This class extends {@link Actor} and introduces additional methods and mechanisms to
 * manage the actor's state and message handling.
 *
 * <p>Subclasses must implement the {@link #initialBehavior()} method to define the initial
 * behavior of the actor. The {@link #preStart()} method can be overridden to perform any
 * initialization tasks before the actor starts processing messages.
 *
 * @param <T> the type of messages that this actor can handle
 */
public abstract class AbstractActor<T> extends Actor<T> {
    private Behavior<T> behavior;

    protected AbstractActor(ActorContext<T> context) {
        super(context);
    }

    @Override
    public final void onStart() {
        preStart();
        behavior = initialBehavior();
    }

    /**
     * Called before the actor starts processing messages. This method can be overridden to perform
     * any initialization tasks that need to be executed before the actor begins its
     * message-handling phase.
     * <p>
     * Typical use cases for this method include setting up initial state, subscribing to event
     * streams, or establishing connections with other actors or external systems. This method is
     * called as part of the {@code onStart()} method, which is invoked when the actor is started.
     */
    protected void preStart() {

    }

    /**
     * Defines the initial behavior of the actor. This method must be implemented by concrete
     * subclasses to provide the starting behavior for the actor when it is first started.
     *
     * <p>The initial behavior determines how the actor will handle its first messages and can be
     * changed over time as the actor transitions through different states or modes of operation.
     *
     * @return the initial {@code Behavior<T>} that defines how the actor should handle incoming
     * messages
     */
    protected abstract Behavior<T> initialBehavior();

    @Override
    public final void onReceive(T t) {
        Behavior<T> ret = behavior.onReceive(context(), t);
        tryUpdateBehavior(ret, t);
    }

    @Override
    public final void onSignal(Signal signal) {
        Behavior<T> ret = behavior.onSignal(context(), signal);
        tryUpdateBehavior(ret, signal);
    }

    private void tryUpdateBehavior(Behavior<T> ret, Object obj) {
        switch (InternalBehavior.getTag(ret)) {
            case SAME -> {
            }
            case COMPOSITE -> {
                var composite = (Behaviors.CompositeBehavior<T>) ret;
                Behavior<T> msgBehavior = composite.msgBehavior();
                Behavior<T> signalBehavior = composite.signalBehavior();
                if (msgBehavior == InternalBehavior.SAME) {
                    if (signalBehavior == InternalBehavior.SAME) {
                        return;
                    }
                    behavior = Behaviors.composite(behavior, signalBehavior);
                } else if (signalBehavior == InternalBehavior.SAME) {
                    behavior = Behaviors.composite(msgBehavior, behavior);
                }
            }
            case MESSAGE_HANDLE -> behavior = ret;
            case STOP -> context().stop();
            case UNHANDLED -> {
                if (obj instanceof Signal) {
                    context().system().getLogger()
                            .warn("{} receive unhandled signal: {}", context().self(), obj);
                } else {
                    context().system().getLogger()
                            .warn("{} receive unhandled message: {}", context().self(), obj);
                }
            }
            default ->
                    throw new IllegalStateException("Unknown Behavior: " + InternalBehavior.getTag(ret));
        }
    }
}
