package io.axor.raft;

import java.time.Duration;

public record ClientConfig(Duration requestTimeout) {
}
