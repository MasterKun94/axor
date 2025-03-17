package io.masterkun.kactor.runtime.stream.grpc;

import io.grpc.ServerBuilder;
import io.masterkun.kactor.runtime.EventDispatcher;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.runtime.Status;
import io.masterkun.kactor.runtime.StatusCode;
import io.masterkun.kactor.runtime.StreamAddress;
import io.masterkun.kactor.runtime.StreamChannel;
import io.masterkun.kactor.runtime.StreamDefinition;
import io.masterkun.kactor.runtime.StreamInChannel;
import io.masterkun.kactor.runtime.StreamOutChannel;
import io.masterkun.kactor.runtime.impl.DefaultEventDispatcher;
import io.masterkun.kactor.runtime.stream.grpc.proto.KActorProto.ActorAddress;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

public class GrpcStreamServerTest {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcStreamServerTest.class);

    private static GrpcStreamServer streamServer;

    @BeforeClass
    public static void setup() throws Exception {

        ServerBuilder<?> builder = ServerBuilder.forPort(10111);
        streamServer = new GrpcStreamServer("test", builder);
        streamServer.start();
        Thread.sleep(100);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        streamServer.shutdown();
    }

    @Test
    public void test() throws InterruptedException {
        EventDispatcher executor = new DefaultEventDispatcher("test-executor");
        StreamDefinition<ActorAddress> selfDefinition = new StreamDefinition<>(
                new StreamAddress("localhost", 10111, "test", "helloworld"),
                SerdeRegistry.defaultInstance().create(MsgType.of(ActorAddress.class))
        );
        Closeable unregisterHook = streamServer.register(new StreamInChannel<ActorAddress>() {
            @Override
            public <OUT> StreamObserver<ActorAddress> open(StreamDefinition<OUT> remote, EventDispatcher executor, Observer observer) {
                LOG.info("Open from {} to {}", remote.address(), selfDefinition.address());
                return new StreamObserver<>() {

                    @Override
                    public void onEnd(Status status) {
                        LOG.info("{} received end stats: {}", selfDefinition.address(), status);
                        assert executor.inExecutor();
                        observer.onEnd(status);
                    }

                    @Override
                    public void onNext(ActorAddress testMessage) {
                        assert executor.inExecutor();
                        LOG.info("{} Received: {}", selfDefinition.address(), testMessage.getName());
                    }
                };
            }

            @Override
            public StreamDefinition<ActorAddress> getSelfDefinition() {
                return selfDefinition;
            }
        }, executor);

        StreamDefinition<ActorAddress> definition2 = new StreamDefinition<>(
                new StreamAddress("localhost", 1001, "test", "test2"),
                SerdeRegistry.defaultInstance().create(MsgType.of(ActorAddress.class))
        );
        StreamOutChannel<ActorAddress> channel = streamServer.get(definition2, executor);
        StreamChannel.StreamObserver<ActorAddress> open = channel.open(selfDefinition, executor, status ->
                LOG.info("{} received end stats: {}", definition2.address(), status));

        open.onNext(ActorAddress.newBuilder().setName("hello").build());
        open.onNext(ActorAddress.newBuilder().setName("world").build());
        open.onEnd(StatusCode.COMPLETE.toStatus());

        Thread.sleep(1000);

    }
}
