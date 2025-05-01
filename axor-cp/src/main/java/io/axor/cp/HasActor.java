package io.axor.cp;

import io.axor.api.ActorRef;

public interface HasActor {
    ActorRef<?> actor();
}
