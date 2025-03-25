package io.masterkun.axor.api;

public record SystemCacheKey(String name, String system, Address publishAddress) {
    public SystemCacheKey(String name, ActorSystem system) {
        this(name, system.name(), system.publishAddress());
    }
}
