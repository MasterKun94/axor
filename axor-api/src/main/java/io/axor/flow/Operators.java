package io.axor.flow;

import java.util.concurrent.Flow;
import java.util.function.Function;

public class Operators {
    public static <T, P> Flow.Subscriber<T> mapOp(Function<T, P> function,
                                                  Flow.Subscriber<P> subscriber) {
        return new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(T item) {
                subscriber.onNext(function.apply(item));
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        };
    }
}
