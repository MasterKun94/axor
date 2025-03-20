package io.masterkun.axor.api.ref;

import java.util.concurrent.TimeUnit;

public interface WeakActorRefQueue<A extends WeakActorRef<?, ?>> {

    A take() throws InterruptedException;

    A poll() throws InterruptedException;

    A poll(long timeout, TimeUnit unit) throws InterruptedException;
}
