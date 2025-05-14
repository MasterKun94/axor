package io.axor.raft.statemachine;

import com.google.protobuf.ByteString;

import java.net.URI;
import java.util.List;

public record Snapshot(long id, ByteString data, List<URI> files) {
}
