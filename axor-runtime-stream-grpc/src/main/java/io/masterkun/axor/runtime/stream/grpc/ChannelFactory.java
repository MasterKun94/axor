package io.masterkun.axor.runtime.stream.grpc;

import io.grpc.ManagedChannel;

public interface ChannelFactory {
    ManagedChannel createChannel(String host, int port);
}
