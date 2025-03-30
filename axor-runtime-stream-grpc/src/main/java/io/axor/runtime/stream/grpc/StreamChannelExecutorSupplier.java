package io.axor.runtime.stream.grpc;

import io.axor.runtime.StreamDefinition;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallExecutorSupplier;

import javax.annotation.Nullable;
import java.util.concurrent.Executor;

public class StreamChannelExecutorSupplier implements ServerCallExecutorSupplier {
    private final GrpcRuntime runtime;
    private final ServiceRegistry serviceRegistry;
    private final ServerCallExecutorSupplier fallback;

    public StreamChannelExecutorSupplier(GrpcRuntime runtime,
                                         ServiceRegistry serviceRegistry,
                                         ServerCallExecutorSupplier fallback) {
        this.runtime = runtime;
        this.serviceRegistry = serviceRegistry;
        this.fallback = fallback;
    }

    @Nullable
    @Override
    public <ReqT, RespT> Executor getExecutor(ServerCall<ReqT, RespT> call, Metadata metadata) {
        if (call.getMethodDescriptor().getServiceName().equals(runtime.getServiceName())) {
            StreamDefinition<?> def = metadata.get(runtime.getServerStreamNameKey());
            return def == null ? null : serviceRegistry.getExecutor(def);
        }
        return fallback == null ? null : fallback.getExecutor(call, metadata);
    }
}
