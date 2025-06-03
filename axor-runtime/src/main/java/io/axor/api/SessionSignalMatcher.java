package io.axor.api;

import io.axor.runtime.Signal;

public interface SessionSignalMatcher {
    boolean matches(Signal signal);
}
