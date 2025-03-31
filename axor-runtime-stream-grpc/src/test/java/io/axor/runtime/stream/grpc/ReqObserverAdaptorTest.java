package io.axor.runtime.stream.grpc;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
import io.axor.runtime.impl.BuiltinSerde;
import io.axor.runtime.stream.grpc.GrpcRuntime.ReqObserverAdaptor;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.DataInput;
import java.io.DataOutput;
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
        ContextMsgMarshaller<String> marshaller =
                new ContextMsgMarshaller<>(new StringBuiltinSerde());

        ReqObserverAdaptor<String> adaptor = new ReqObserverAdaptor<>(mockReq, marshaller,
                Mockito.mock(EventDispatcher.class));

        adaptor.onNext("testValue");

        verify(mockReq).onNext(any(InputStream.class));
    }

    @Test
    public void testOnEndWithCompleteStatus() {
        StreamObserver<InputStream> mockReq = Mockito.mock(StreamObserver.class);
        ContextMsgMarshaller<String> marshaller =
                new ContextMsgMarshaller<>(new StringBuiltinSerde());

        ReqObserverAdaptor<String> adaptor = new ReqObserverAdaptor<>(mockReq, marshaller,
                Mockito.mock(EventDispatcher.class));

        adaptor.onEnd(StatusCode.COMPLETE.toStatus());

        verify(mockReq).onCompleted();
    }

    @Test
    public void testOnEndWithErrorStatus() {
        StreamObserver<InputStream> mockReq = Mockito.mock(StreamObserver.class);
        ContextMsgMarshaller<String> marshaller =
                new ContextMsgMarshaller<>(new StringBuiltinSerde());

        ReqObserverAdaptor<String> adaptor = new ReqObserverAdaptor<>(mockReq, marshaller,
                Mockito.mock(EventDispatcher.class));

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
        ContextMsgMarshaller<String> marshaller =
                new ContextMsgMarshaller<>(new StringBuiltinSerde());

        ReqObserverAdaptor<String> adaptor = new ReqObserverAdaptor<>(mockReq, marshaller,
                Mockito.mock(EventDispatcher.class));

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

    private static class StringBuiltinSerde implements BuiltinSerde<String> {

        @Override
        public MsgType<String> getType() {
            return MsgType.of(String.class);
        }

        @Override
        public void doSerialize(String obj, DataOutput out) throws IOException {
            out.writeUTF(obj);
        }

        @Override
        public String doDeserialize(DataInput in) throws IOException {
            return in.readUTF();
        }
    }
}
