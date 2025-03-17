package io.masterkun.kactor.api;

@SuppressWarnings("rawtypes")
public enum BehaviorInternal implements Behavior {
    SAME,
    STOP,
    UNHANDLED,
    MESSAGE_HANDLE;

    public static BehaviorInternal getTag(Behavior<?> behavior) {
        return behavior instanceof BehaviorInternal internal ? internal : MESSAGE_HANDLE;
    }

    @Override
    public Behavior onReceive(ActorContext context, Object message) {
        throw new UnsupportedOperationException();
    }
}
