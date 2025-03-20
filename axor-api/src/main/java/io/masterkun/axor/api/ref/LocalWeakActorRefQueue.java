package io.masterkun.axor.api.ref;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LocalWeakActorRefQueue implements WeakActorRefQueue<LocalWeakActorRef<?>> {
    private final BlockingQueue<LocalWeakActorRef<?>> queue = new LinkedBlockingQueue<>();

    void enqueue(LocalWeakActorRef<?> actor) {
        queue.add(actor);
    }

    @Override
    public LocalWeakActorRef<?> take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public LocalWeakActorRef<?> poll() {
        return queue.poll();
    }

    @Override
    public LocalWeakActorRef<?> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }
}
