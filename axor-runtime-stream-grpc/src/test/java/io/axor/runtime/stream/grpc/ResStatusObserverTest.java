package io.axor.runtime.stream.grpc;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.Signal;
import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
import io.axor.runtime.StreamChannel;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.stream.grpc.GrpcRuntime.ResStatusObserver;
import org.junit.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.refEq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ResStatusObserverTest {

    @Test
    public void testOnNextWithValidResStatus() {
        StreamDefinition<?> remoteDefinition = mock(StreamDefinition.class);
        StreamDefinition<?> selfDefinition = mock(StreamDefinition.class);
        StreamChannel.StreamObserver<Signal> observer =
                mock(StreamChannel.StreamObserver.class);
        EventDispatcher executor = mock(EventDispatcher.class);

        ResStatusObserver resStatusObserver = new ResStatusObserver(remoteDefinition,
                selfDefinition, observer, executor, SerdeRegistry.defaultInstance());
        resStatusObserver.onNext(StreamUtils.endSignal(StatusCode.COMPLETE.code, "Complete"));
        verify(observer).onEnd(eq(new Status(StatusCode.COMPLETE.code, null)));
    }

    @Test
    public void testOnNextWithInvalidResStatus() {
        StreamDefinition<?> remoteDefinition = mock(StreamDefinition.class);
        StreamDefinition<?> selfDefinition = mock(StreamDefinition.class);
        StreamChannel.StreamObserver<Signal> observer =
                mock(StreamChannel.StreamObserver.class);
        EventDispatcher executor = mock(EventDispatcher.class);

        ResStatusObserver resStatusObserver = new ResStatusObserver(remoteDefinition,
                selfDefinition, observer, executor, SerdeRegistry.defaultInstance());
        resStatusObserver.onNext(StreamUtils.endSignal(StatusCode.SYSTEM_ERROR.code, "Error"));
        verify(observer).onEnd(refEq(new Status(StatusCode.SYSTEM_ERROR.code, null), "cause"));
    }

    @Test
    public void testOnNextWhenAlreadyDone() {
        StreamDefinition<?> remoteDefinition = mock(StreamDefinition.class);
        StreamDefinition<?> selfDefinition = mock(StreamDefinition.class);
        StreamChannel.StreamObserver<Signal> observer =
                mock(StreamChannel.StreamObserver.class);
        EventDispatcher executor = mock(EventDispatcher.class);
        when(executor.inExecutor()).thenReturn(true);

        ResStatusObserver resStatusObserver = new ResStatusObserver(remoteDefinition,
                selfDefinition, observer, executor, SerdeRegistry.defaultInstance());
        resStatusObserver.onCompleted();
        resStatusObserver.onNext(StreamUtils.endSignal(StatusCode.COMPLETE.code, "Complete"));
        verify(observer, times(1)).onEnd(any(Status.class));
    }

    @Test
    public void testOnError() {
        StreamDefinition<?> remoteDefinition = mock(StreamDefinition.class);
        StreamDefinition<?> selfDefinition = mock(StreamDefinition.class);
        StreamChannel.StreamObserver<Signal> observer =
                mock(StreamChannel.StreamObserver.class);
        EventDispatcher executor = mock(EventDispatcher.class);
        when(executor.inExecutor()).thenReturn(true);
        ResStatusObserver resStatusObserver = new ResStatusObserver(remoteDefinition,
                selfDefinition, observer, executor, SerdeRegistry.defaultInstance());
        Throwable t = new RuntimeException("Test Exception");
        resStatusObserver.onError(t);
        verify(observer).onEnd(any(Status.class));
    }

    @Test
    public void testOnCompleted() {
        StreamDefinition<?> remoteDefinition = mock(StreamDefinition.class);
        StreamDefinition<?> selfDefinition = mock(StreamDefinition.class);
        StreamChannel.StreamObserver<Signal> observer =
                mock(StreamChannel.StreamObserver.class);
        EventDispatcher executor = mock(EventDispatcher.class);
        when(executor.inExecutor()).thenReturn(true);
        ResStatusObserver resStatusObserver = new ResStatusObserver(remoteDefinition,
                selfDefinition, observer, executor, SerdeRegistry.defaultInstance());
        resStatusObserver.onCompleted();
        verify(observer).onEnd(eq(new Status(StatusCode.COMPLETE.code, null)));
    }
}
