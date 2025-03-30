package io.axor.api;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.masterkun.stateeasy.concurrent.EventPromise;
import io.masterkun.stateeasy.concurrent.EventStage;
import io.masterkun.stateeasy.concurrent.EventStageListener;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ActorPatterns {
    private static final AtomicInteger ADDER = new AtomicInteger();

    /**
     * Sends a request to the specified actor and waits for a response of the expected type.
     *
     * @param <REQ>   the type of the request message
     * @param <RES>   the type of the expected response message
     * @param actor   the {@code ActorRef} representing the recipient of the request
     * @param request the request message to be sent
     * @param resType the message type of the expected response
     * @param timeout the duration to wait for a response before timing out
     * @param system  the actor system in which the operation is to be performed
     * @return an {@code EventStage<RES>} that will complete with the response or a failure if the
     * timeout is reached
     */
    public static <REQ, RES> EventStage<RES> ask(ActorRef<REQ> actor,
                                                 REQ request,
                                                 MsgType<RES> resType,
                                                 Duration timeout,
                                                 ActorSystem system) {
        EventDispatcher dispatcher = EventDispatcher.current();
        if (dispatcher == null) {
            dispatcher = system.getDispatcherGroup().nextDispatcher();
        }
        var promise = EventPromise.<RES>newPromise(dispatcher);
        var askActor = system.<RES>start(
                c -> new AskActor<>(c, promise, resType, timeout),
                "sys/ask-pattern_" + ADDER.getAndIncrement(), dispatcher);
        actor.tell(request, askActor);
        return promise;
    }

    private static class AskActor<RES> extends Actor<RES> {
        private final EventPromise<RES> promise;
        private final MsgType<RES> resType;

        protected AskActor(ActorContext<RES> context,
                           EventPromise<RES> promise,
                           MsgType<RES> resType,
                           Duration timeout) {
            super(context);
            this.promise = promise;
            this.resType = resType;
            context.dispatcher().timeout(promise, timeout.toMillis(), TimeUnit.MILLISECONDS);
            promise.addListener(new EventStageListener<>() {
                @Override
                public void success(RES res) {
                    assert context.dispatcher().inExecutor();
                    context.stop();
                }

                @Override
                public void failure(Throwable throwable) {
                    assert context.dispatcher().inExecutor();
                    context.stop();
                }
            });
        }

        @Override
        public void onReceive(RES res) {
            promise.success(res);
        }

        @Override
        public MsgType<RES> msgType() {
            return resType;
        }
    }
}
