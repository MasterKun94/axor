package io.masterkun.axor.runtime;

public interface StreamServerBuilder {
    StreamServerBuilder system(String system);

    StreamServerBuilder serdeRegistry(SerdeRegistry serdeRegistry);

    StreamServerBuilder deadLetterHandler(DeadLetterHandlerFactory deadLetterHandler);

    StreamServer build();
}
