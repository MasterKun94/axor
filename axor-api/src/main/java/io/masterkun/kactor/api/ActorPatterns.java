package io.masterkun.kactor.api;

import io.masterkun.kactor.runtime.MsgType;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ActorPatterns {
    private static final AtomicInteger ADDER = new AtomicInteger();

    /**
     * Sends a request to the specified actor and waits for a response of the specified type.
     *
     * @param <REQ>   the type of the request message
     * @param <RES>   the type of the expected response message
     * @param actor   the reference to the actor to which the request is sent
     * @param request the request message to send
     * @param resType the message type of the expected response
     * @param timeout the duration to wait for the response before timing out
     * @param system  the actor system in which the actors are running
     * @return a CompletableFuture that will be completed with the response or an exception if the operation fails or times out
     */
    public static <REQ, RES> CompletableFuture<RES> ask(ActorRef<REQ> actor,
                                                        REQ request,
                                                        MsgType<RES> resType,
                                                        Duration timeout,
                                                        ActorSystem system) {
        var future = new CompletableFuture<RES>();
        var askActor = system.<RES>start(
                c -> new AskActor<>(c, future, resType, timeout),
                "AskActor_" + ADDER.getAndIncrement());
        actor.tell(request, askActor);
        return future;
    }

    private static class AskActor<RES> extends Actor<RES> {
        private final CompletableFuture<RES> future;
        private final MsgType<RES> resType;

        protected AskActor(ActorContext<RES> context,
                           CompletableFuture<RES> future,
                           MsgType<RES> resType,
                           Duration timeout) {
            super(context);
            this.future = future;
            this.resType = resType;
            context.executor().timeout(future, timeout.toMillis(), TimeUnit.MILLISECONDS);
            future.whenCompleteAsync((r, t) -> {
                context.stop();
            }, context.executor());
        }

        @Override
        public void onReceive(RES res) {
            future.complete(res);
        }

        @Override
        public MsgType<RES> msgType() {
            return resType;
        }
    }
}
