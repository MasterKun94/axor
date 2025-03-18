package io.masterkun.axor.runtime.stream.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.Status;
import io.masterkun.axor.runtime.StatusCode;
import io.masterkun.axor.runtime.StreamAddress;
import io.masterkun.axor.runtime.StreamChannel;
import io.masterkun.axor.runtime.StreamDefinition;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GrpcRuntimeTest {

    @Test
    public void testClientCallOnNextAfterCompletion() {
        Channel channel = Mockito.mock(Channel.class);
        SerdeRegistry registry = SerdeRegistry.defaultInstance();
        StreamDefinition<?> selfDefinition = new StreamDefinition<>(
                new StreamAddress("localhost", 8080, "service", "call"),
                registry.create(MsgType.of(Timestamp.class)));
        StreamDefinition<Timestamp> definition = new StreamDefinition<>(
                new StreamAddress("localhost", 8081, "service", "call"),
                registry.create(MsgType.of(Timestamp.class)));
        EventDispatcher executor = Mockito.mock(EventDispatcher.class);
        StreamChannel.Observer observer = Mockito.mock(StreamChannel.Observer.class);

        when(channel.newCall(any(MethodDescriptor.class), any())).thenReturn(Mockito.mock(io.grpc.ClientCall.class));

        GrpcRuntime grpcRuntime = new GrpcRuntime("system", registry, null, null, null);
        StreamChannel.StreamObserver<Timestamp> streamObserver = grpcRuntime.clientCall(channel,
                selfDefinition, definition, executor, observer);

        Metadata metadata = new Metadata();
        metadata.put(grpcRuntime.getServerStreamNameKey(), definition);
        metadata.put(grpcRuntime.getServerStreamNameKey(), selfDefinition);

        verify(channel).newCall(eq(grpcRuntime.getMethodDescriptor()), any());
        verify(observer, never()).onEnd(any(Status.class));
        verify(executor, never()).inExecutor();

        streamObserver.onEnd(StatusCode.COMPLETE.toStatus());

        try {
            streamObserver.onNext(Timestamp.newBuilder().setSeconds(123).build());
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("already completed", e.getMessage());
        }
    }
}
