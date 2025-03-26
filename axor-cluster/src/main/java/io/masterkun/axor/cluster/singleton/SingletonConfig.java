package io.masterkun.axor.cluster.singleton;

import java.util.List;

public record SingletonConfig(String name, List<String> requireRoles) {
}
