package io.masterkun.kactor.runtime.stream.grpc;

import io.grpc.ManagedChannelBuilder;

public interface ChannelBuilderModifier<T extends ManagedChannelBuilder<?>> {
    void modify(T builder);
}
