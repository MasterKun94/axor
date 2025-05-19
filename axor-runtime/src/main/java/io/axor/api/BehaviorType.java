package io.axor.api;

enum BehaviorType {
    SAME,
    STOP,
    UNHANDLED,
    MESSAGE_HANDLE,
    COMPOSITE,
    CONSUME_BUFFER,
    ;

    public static BehaviorType getTag(Behavior<?> behavior) {
        return behavior instanceof Behaviors.SpecialBehavior<?> s ?
                s.type() : MESSAGE_HANDLE;
    }

    public boolean isMatch(Behavior<?> behavior) {
        return behavior instanceof Behaviors.SpecialBehavior<?> s && s.type() == this;
    }
}
