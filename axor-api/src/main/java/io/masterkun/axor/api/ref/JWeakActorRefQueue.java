package io.masterkun.axor.api.ref;

import io.masterkun.axor.api.ActorRef;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.TimeUnit;

public class JWeakActorRefQueue implements WeakActorRefQueue<JWeakActorRef<?>> {
    private final ReferenceQueue<ActorRef<?>> queue = new ReferenceQueue<>();

    public ReferenceQueue<ActorRef<?>> getQueue() {
        return queue;
    }

    @Override
    public JWeakActorRef<?> take() throws InterruptedException {
        return (JWeakActorRef<?>) queue.remove();
    }

    @Override
    public JWeakActorRef<?> poll() {
        return (JWeakActorRef<?>) queue.poll();
    }

    @Override
    public JWeakActorRef<?> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return (JWeakActorRef<?>) queue.remove(unit.toMillis(timeout));
    }
}
