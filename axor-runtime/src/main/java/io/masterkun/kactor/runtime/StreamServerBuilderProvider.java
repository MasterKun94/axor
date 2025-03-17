package io.masterkun.kactor.runtime;

public interface StreamServerBuilderProvider extends Provider<StreamServerBuilder> {
    @Override
    default String group() {
        return "server";
    }
}
