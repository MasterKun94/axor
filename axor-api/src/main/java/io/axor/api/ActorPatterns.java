package io.axor.api;

import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.concurrent.EventStageObserver;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class ActorPatterns {
    private static final AtomicInteger ADDER = new AtomicInteger();
    private static final LongAdder ACK_ID_ADDER = new LongAdder();

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
                "sys/pattern-ack-" + ADDER.getAndIncrement(), dispatcher);
        actor.tell(request, askActor);
        return promise;
    }

    /**
     * Sends a message to the specified actor and waits for an acknowledgment.
     *
     * @param <T>     the type of the message
     * @param ref     the {@code ActorRef} representing the recipient of the message
     * @param msg     the message to be sent
     * @param timeout the duration to wait for an acknowledgment before timing out
     * @param system  the actor system in which the operation is to be performed
     * @return an {@code EventStage<Void>} that will complete when the acknowledgment is received or
     * fail if the timeout is reached
     */
    public static <T> EventStage<Void> tellWithAck(ActorRef<T> ref, T msg, Duration timeout,
                                                   ActorSystem system) {
        EventDispatcher dispatcher = EventDispatcher.current();
        if (dispatcher == null) {
            dispatcher = system.getDispatcherGroup().nextDispatcher();
        }
        var promise = EventPromise.<Void>newPromise(dispatcher);
        long l = ACK_ID_ADDER.longValue();
        ACK_ID_ADDER.increment();
        var sender = system.start(
                c -> new TellWithAckActor(c, promise, l, timeout),
                "sys/pattern-ack-" + ADDER.getAndIncrement(), dispatcher);
        try (var ignore = ReliableDelivery.wrap(EventContext.current(), l).openScope()) {
            ref.tell(msg, sender);
        }
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
            promise.observe(new EventStageObserver<>() {
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

    private static class TellWithAckActor extends Actor<Object> {
        private static final Logger LOG = LoggerFactory.getLogger(TellWithAckActor.class);
        private final EventPromise<Void> promise;
        private final long expectId;

        protected TellWithAckActor(ActorContext<Object> context, EventPromise<Void> promise,
                                   long expectId, Duration timeout) {
            super(context);
            this.promise = promise;
            this.expectId = expectId;
            context.dispatcher().timeout(promise, timeout.toMillis(), TimeUnit.MILLISECONDS);
            promise.observe(new EventStageObserver<>() {
                @Override
                public void success(Void res) {
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
        public void onReceive(Object s) {
            LOG.warn("Receive unexpected msg: {}", s);
        }

        @Override
        public void onSignal(Signal signal) {
            if (signal instanceof ReliableDelivery.MsgAckSuccess(var l)) {
                if (l == expectId) {
                    promise.success(null);
                    return;
                }
            } else if (signal instanceof ReliableDelivery.MsgAckFailed(var l, var cause)) {
                if (l == expectId) {
                    promise.failure(cause);
                    return;
                }
            }
            LOG.warn("Receive unexpected signal: {}", signal);
        }

        @Override
        public MsgType<Object> msgType() {
            return MsgType.of(Object.class);
        }
    }
}
