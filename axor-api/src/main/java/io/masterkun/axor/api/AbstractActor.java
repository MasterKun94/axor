package io.masterkun.axor.api;

/**
 * Abstract base class for actors that provides a more structured way to define actor behavior.
 * This class extends the {@code Actor<T>} class and introduces the concept of behaviors, which are
 * encapsulated in the {@code Behavior<T>} interface. The behavior can be changed dynamically based on
 * the messages received by the actor.
 *
 * <p>Subclasses must implement the {@code initialBehavior()} method to provide the initial behavior
 * of the actor. The {@code preStart()} method can be overridden to perform any initialization tasks
 * before the actor starts processing messages.
 *
 * @param <T> the type of messages that this actor can handle
 */
public abstract class AbstractActor<T> extends Actor<T> {
    private Behavior<T> behavior;

    protected AbstractActor(ActorContext<T> context) {
        super(context);
    }

    @Override
    public void onStart() {
        preStart();
        behavior = initialBehavior();
    }

    /**
     * Called before the actor starts processing messages. This method can be overridden to perform any
     * initialization tasks that need to be executed before the actor begins its message-handling phase.
     * <p>
     * Typical use cases for this method include setting up initial state, subscribing to event streams,
     * or establishing connections with other actors or external systems. This method is called as part of
     * the {@code onStart()} method, which is invoked when the actor is started.
     */
    protected void preStart() {

    }

    /**
     * Defines the initial behavior of the actor.
     *
     * <p>This method must be implemented by subclasses to provide the starting behavior of the actor.
     * The returned {@code Behavior<T>} object encapsulates the logic for handling messages and can be
     * dynamically changed based on the messages received.
     *
     * @return the initial {@code Behavior<T>} that defines how the actor should handle messages
     */
    protected abstract Behavior<T> initialBehavior();

    @Override
    public void onReceive(T t) {
        Behavior<T> ret = behavior.onReceive(context(), t);
        switch (BehaviorInternal.getTag(ret)) {
            case SAME -> {
            }
            case MESSAGE_HANDLE -> behavior = ret;
            case STOP -> context().stop();
            case UNHANDLED -> context().system().logger()
                    .warn("{} receive unhandled message: {}", context().self(), t);
            default ->
                    throw new IllegalStateException("Unknown Behavior: " + BehaviorInternal.getTag(ret));
        }
    }
}
