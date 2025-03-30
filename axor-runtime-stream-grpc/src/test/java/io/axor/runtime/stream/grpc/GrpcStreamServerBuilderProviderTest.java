package io.axor.runtime.stream.grpc;

import com.typesafe.config.ConfigFactory;
import io.axor.runtime.LoggingDeadLetterHandlerFactory;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamServer;
import io.axor.runtime.StreamServerBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class GrpcStreamServerBuilderProviderTest {

    @Test
    public void test() throws Exception {
        GrpcStreamServerBuilderProvider provider = new GrpcStreamServerBuilderProvider();
        StreamServerBuilder builder = provider.createFromRootConfig(ConfigFactory.load().resolve());
        StreamServer server = builder.system("test")
                .serdeRegistry(SerdeRegistry.defaultInstance())
                .deadLetterHandler(new LoggingDeadLetterHandlerFactory(LoggerFactory.getLogger(GrpcStreamServerBuilderProviderTest.class)))
                .build().start();
        Assert.assertEquals(12110, server.bindPort());
        server.shutdownAsync().join();
    }

}
