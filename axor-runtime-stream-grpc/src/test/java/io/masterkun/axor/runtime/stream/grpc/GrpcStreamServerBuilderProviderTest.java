package io.masterkun.axor.runtime.stream.grpc;

import com.typesafe.config.ConfigFactory;
import io.masterkun.axor.runtime.LoggingDeadLetterHandlerFactory;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.StreamServer;
import io.masterkun.axor.runtime.StreamServerBuilder;
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
