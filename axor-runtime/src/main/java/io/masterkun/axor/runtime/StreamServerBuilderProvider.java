package io.masterkun.axor.runtime;

public interface StreamServerBuilderProvider extends Provider<StreamServerBuilder> {
    @Override
    default String group() {
        return "server";
    }
}
