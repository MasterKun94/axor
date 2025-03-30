package io.axor.runtime.stream.grpc;

import com.google.protobuf.Timestamp;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamDefinition;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallExecutorSupplier;
import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
public class StreamChannelExecutorSupplierTest {
    private static final Metadata.Key<StreamDefinition<?>> SERVICE_NAME_KEY = Metadata.Key.of(
            "service-name", new StreamDefinitionBinaryMarshaller(SerdeRegistry.defaultInstance()));

    @Test
    public void testGetExecutorForMatchingServiceName() {
        GrpcRuntime runtime = mock(GrpcRuntime.class);
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        ServerCallExecutorSupplier fallback = mock(ServerCallExecutorSupplier.class);

        when(runtime.getServiceName()).thenReturn("testService");
        when(runtime.getServerStreamNameKey()).thenReturn(SERVICE_NAME_KEY);

        StreamChannelExecutorSupplier supplier = new StreamChannelExecutorSupplier(runtime,
                serviceRegistry, fallback);

        ServerCall<?, ?> call = mock(ServerCall.class);
        MethodDescriptor methodDescriptor = mock(MethodDescriptor.class);
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(call.getMethodDescriptor().getServiceName()).thenReturn("testService");

        Metadata metadata = new Metadata();
        StreamDefinition def = new StreamDefinition<>(new StreamAddress("host", 123,
                "testService", "call"),
                SerdeRegistry.defaultInstance().create(MsgType.of(Timestamp.class)));
        metadata.put(SERVICE_NAME_KEY, def);

        EventDispatcher expectedExecutor = mock(EventDispatcher.class);
        when(serviceRegistry.getExecutor(eq(def))).thenReturn(expectedExecutor);

        Executor actualExecutor = supplier.getExecutor(call, metadata);

        assertEquals(expectedExecutor, actualExecutor);
    }

    @Test
    public void testGetExecutorForNonMatchingServiceNameWithFallback() {
        GrpcRuntime runtime = mock(GrpcRuntime.class);
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        ServerCallExecutorSupplier fallback = mock(ServerCallExecutorSupplier.class);

        when(runtime.getServiceName()).thenReturn("testService");
        when(runtime.getServerStreamNameKey()).thenReturn(SERVICE_NAME_KEY);

        StreamChannelExecutorSupplier supplier = new StreamChannelExecutorSupplier(runtime,
                serviceRegistry, fallback);

        ServerCall<?, ?> call = mock(ServerCall.class);
        MethodDescriptor methodDescriptor = mock(MethodDescriptor.class);
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(call.getMethodDescriptor().getServiceName()).thenReturn("otherService");

        Metadata metadata = new Metadata();

        Executor expectedExecutor = mock(Executor.class);
        when(fallback.getExecutor(call, metadata)).thenReturn(expectedExecutor);

        Executor actualExecutor = supplier.getExecutor(call, metadata);

        assertEquals(expectedExecutor, actualExecutor);
    }

    @Test
    public void testGetExecutorForNonMatchingServiceNameWithoutFallback() {
        GrpcRuntime runtime = mock(GrpcRuntime.class);
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);

        when(runtime.getServiceName()).thenReturn("testService");
        when(runtime.getServerStreamNameKey()).thenReturn(SERVICE_NAME_KEY);

        StreamChannelExecutorSupplier supplier = new StreamChannelExecutorSupplier(runtime,
                serviceRegistry, null);

        ServerCall<?, ?> call = mock(ServerCall.class);
        MethodDescriptor methodDescriptor = mock(MethodDescriptor.class);
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(call.getMethodDescriptor().getServiceName()).thenReturn("otherService");

        Metadata metadata = new Metadata();

        Executor actualExecutor = supplier.getExecutor(call, metadata);

        assertNull(actualExecutor);
    }

    @Test
    public void testGetExecutorForMatchingServiceNameButNoStreamDefinition() {
        GrpcRuntime runtime = mock(GrpcRuntime.class);
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        ServerCallExecutorSupplier fallback = mock(ServerCallExecutorSupplier.class);

        when(runtime.getServiceName()).thenReturn("testService");
        when(runtime.getServerStreamNameKey()).thenReturn(SERVICE_NAME_KEY);

        StreamChannelExecutorSupplier supplier = new StreamChannelExecutorSupplier(runtime,
                serviceRegistry, fallback);

        ServerCall<?, ?> call = mock(ServerCall.class);
        MethodDescriptor methodDescriptor = mock(MethodDescriptor.class);
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(call.getMethodDescriptor().getServiceName()).thenReturn("testService");

        Metadata metadata = new Metadata();

        Executor actualExecutor = supplier.getExecutor(call, metadata);

        assertNull(actualExecutor);
    }
}
