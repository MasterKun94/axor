package io.masterkun.kactor.runtime.stream.grpc;

import com.typesafe.config.Config;
import io.masterkun.kactor.runtime.StreamServerBuilder;
import io.masterkun.kactor.runtime.StreamServerBuilderProvider;

public class GrpcStreamServerBuilderProvider implements StreamServerBuilderProvider {
    @Override
    public int priority() {
        return 10;
    }

    @Override
    public String name() {
        return "grpc";
    }

    @Override
    public StreamServerBuilder create() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamServerBuilder create(Config config) {
        return GrpcStreamServerBuilder.forServer(config);
    }
}
