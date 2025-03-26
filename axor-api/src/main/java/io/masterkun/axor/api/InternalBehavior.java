package io.masterkun.axor.api;

@SuppressWarnings("rawtypes")
public enum InternalBehavior implements Behavior {
    SAME,
    STOP,
    UNHANDLED,
    MESSAGE_HANDLE,
    COMPOSITE;

    public static InternalBehavior getTag(Behavior<?> behavior) {
        return behavior instanceof InternalBehavior internal ? internal :
                behavior instanceof Behaviors.CompositeBehavior<?> ? COMPOSITE : MESSAGE_HANDLE;
    }

    @Override
    public Behavior onReceive(ActorContext context, Object message) {
        throw new UnsupportedOperationException();
    }
}
