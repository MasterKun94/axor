package io.masterkun.axor.runtime.stream.grpc;

import io.grpc.MethodDescriptor;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import io.masterkun.axor.runtime.Status;
import io.masterkun.axor.runtime.StatusCode;
import io.masterkun.axor.runtime.stream.grpc.GrpcRuntime.ReqObserverAdaptor;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ReqObserverAdaptorTest {

    @Test
    public void testOnNext() {
        StreamObserver<InputStream> mockReq = Mockito.mock(StreamObserver.class);
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes()); // Mocked for testing
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return IOUtils.toString(stream); // Mocked for testing
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        ReqObserverAdaptor<String> adaptor = new ReqObserverAdaptor<>(mockReq, marshaller);

        adaptor.onNext("testValue");

        verify(mockReq).onNext(any(InputStream.class));
    }

    @Test
    public void testOnEndWithCompleteStatus() {
        StreamObserver<InputStream> mockReq = Mockito.mock(StreamObserver.class);
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return null; // Mocked for testing
            }

            @Override
            public String parse(InputStream stream) {
                return null; // Mocked for testing
            }
        };

        ReqObserverAdaptor<String> adaptor = new ReqObserverAdaptor<>(mockReq, marshaller);

        adaptor.onEnd(StatusCode.COMPLETE.toStatus());

        verify(mockReq).onCompleted();
    }

    @Test
    public void testOnEndWithErrorStatus() {
        StreamObserver<InputStream> mockReq = Mockito.mock(StreamObserver.class);
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes()); // Mocked for testing
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return IOUtils.toString(stream); // Mocked for testing
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        ReqObserverAdaptor<String> adaptor = new ReqObserverAdaptor<>(mockReq, marshaller);

        Status errorStatus = StatusCode.SYSTEM_ERROR.toStatus();

        adaptor.onEnd(errorStatus);

        verify(mockReq).onError(argThat(e -> {
            StatusException check = StreamUtils.toStatusException(errorStatus);
            return e instanceof StatusException se &&
                   se.getStatus().getCode().equals(check.getStatus().getCode());
        }));
    }

    @Test
    public void testOnNextAfterCompletion() {
        StreamObserver<InputStream> mockReq = Mockito.mock(StreamObserver.class);
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return null; // Mocked for testing
            }

            @Override
            public String parse(InputStream stream) {
                return null; // Mocked for testing
            }
        };

        ReqObserverAdaptor<String> adaptor = new ReqObserverAdaptor<>(mockReq, marshaller);

        adaptor.onEnd(StatusCode.COMPLETE.toStatus());

        AtomicBoolean exceptionThrown = new AtomicBoolean(false);
        try {
            adaptor.onNext("testValue");
        } catch (IllegalArgumentException e) {
            exceptionThrown.set(true);
        }

        assertTrue(exceptionThrown.get());
        verify(mockReq, never()).onNext(any(InputStream.class));
    }
}
