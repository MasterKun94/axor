package io.masterkun.axor.runtime.stream.grpc;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.StatusCode;
import io.masterkun.axor.runtime.StreamChannel;
import io.masterkun.axor.runtime.stream.grpc.GrpcRuntime.ResObserverAdaptor;
import io.masterkun.axor.runtime.stream.grpc.proto.KActorProto;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.InputStream;

import static io.masterkun.axor.runtime.stream.grpc.StreamUtils.fromStatusException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ResObserverAdaptorTest {

    @Test
    public void testOnNextSuccessfullyParsesAndForwardsValue() {
        EventDispatcher executor = Mockito.mock(EventDispatcher.class);
        StreamChannel.StreamObserver open = Mockito.mock(StreamChannel.StreamObserver.class);
        MethodDescriptor.Marshaller<InputStream> msgMarshaller =
                Mockito.mock(MethodDescriptor.Marshaller.class);
        StreamObserver<KActorProto.ResStatus> resObserver = Mockito.mock(StreamObserver.class);
        InputStream value = Mockito.mock(InputStream.class);

        when(executor.inExecutor()).thenReturn(true);
        when(msgMarshaller.parse(value)).thenReturn(value);

        ResObserverAdaptor adaptor = new ResObserverAdaptor(executor, open, msgMarshaller,
                resObserver);
        adaptor.onNext(value);

        verify(open).onNext(value);
    }

    @Test
    public void testOnNextThrowsExceptionIfNotInExecutor() {
        EventDispatcher executor = Mockito.mock(EventDispatcher.class);
        StreamChannel.StreamObserver<?> open = Mockito.mock(StreamChannel.StreamObserver.class);
        MethodDescriptor.Marshaller<InputStream> msgMarshaller =
                Mockito.mock(MethodDescriptor.Marshaller.class);
        StreamObserver<KActorProto.ResStatus> resObserver = Mockito.mock(StreamObserver.class);
        InputStream value = Mockito.mock(InputStream.class);

        when(executor.inExecutor()).thenReturn(false);

        ResObserverAdaptor adaptor = new ResObserverAdaptor(executor, open, msgMarshaller,
                resObserver);
        try {
            adaptor.onNext(value);
            verifyNoInteractions(open);
        } catch (AssertionError e) {
            // Expected to throw an AssertionError
        }
    }

    @Test
    public void testOnErrorMarksAsDoneAndCallsOnEndWithStatus() {
        EventDispatcher executor = Mockito.mock(EventDispatcher.class);
        StreamChannel.StreamObserver<?> open = Mockito.mock(StreamChannel.StreamObserver.class);
        MethodDescriptor.Marshaller<InputStream> msgMarshaller =
                Mockito.mock(MethodDescriptor.Marshaller.class);
        StreamObserver<KActorProto.ResStatus> resObserver = Mockito.mock(StreamObserver.class);
        Throwable t = new StatusRuntimeException(Status.ABORTED);

        when(executor.inExecutor()).thenReturn(true);

        ResObserverAdaptor adaptor = new ResObserverAdaptor(executor, open, msgMarshaller,
                resObserver);
        adaptor.onError(t);

        verify(open).onEnd(fromStatusException(t));
        verify(resObserver).onCompleted();
    }

    @Test
    public void testOnErrorDoesNothingIfAlreadyDone() {
        EventDispatcher executor = Mockito.mock(EventDispatcher.class);
        StreamChannel.StreamObserver<?> open = Mockito.mock(StreamChannel.StreamObserver.class);
        MethodDescriptor.Marshaller<InputStream> msgMarshaller =
                Mockito.mock(MethodDescriptor.Marshaller.class);
        StreamObserver<KActorProto.ResStatus> resObserver = Mockito.mock(StreamObserver.class);
        Throwable t = new StatusRuntimeException(Status.ABORTED);

        when(executor.inExecutor()).thenReturn(true);

        ResObserverAdaptor adaptor = new ResObserverAdaptor(executor, open, msgMarshaller,
                resObserver);
        adaptor.setDone(true);
        adaptor.onError(t);

        verifyNoInteractions(open);
        verifyNoInteractions(resObserver);
    }

    @Test
    public void testOnCompletedMarksAsDoneAndCallsOnEndWithCompleteStatus() {
        EventDispatcher executor = Mockito.mock(EventDispatcher.class);
        StreamChannel.StreamObserver<?> open = Mockito.mock(StreamChannel.StreamObserver.class);
        MethodDescriptor.Marshaller<InputStream> msgMarshaller =
                Mockito.mock(MethodDescriptor.Marshaller.class);
        StreamObserver<KActorProto.ResStatus> resObserver = Mockito.mock(StreamObserver.class);

        when(executor.inExecutor()).thenReturn(true);

        ResObserverAdaptor adaptor = new ResObserverAdaptor(executor, open, msgMarshaller,
                resObserver);
        adaptor.onCompleted();

        verify(open).onEnd(StatusCode.COMPLETE.toStatus());
        verify(resObserver).onCompleted();
    }

    @Test
    public void testOnCompletedNotThrowsExceptionIfAlreadyDone() {
        EventDispatcher executor = Mockito.mock(EventDispatcher.class);
        StreamChannel.StreamObserver<?> open = Mockito.mock(StreamChannel.StreamObserver.class);
        MethodDescriptor.Marshaller<InputStream> msgMarshaller =
                Mockito.mock(MethodDescriptor.Marshaller.class);
        StreamObserver<KActorProto.ResStatus> resObserver = Mockito.mock(StreamObserver.class);

        when(executor.inExecutor()).thenReturn(true);

        ResObserverAdaptor adaptor = new ResObserverAdaptor(executor, open, msgMarshaller,
                resObserver);
        adaptor.setDone(true);

        adaptor.onCompleted();
        verifyNoInteractions(open);
        verify(resObserver).onCompleted();
    }
}
