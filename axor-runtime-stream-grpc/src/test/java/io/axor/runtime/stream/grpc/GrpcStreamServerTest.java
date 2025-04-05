package io.axor.runtime.stream.grpc;

import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.Signal;
import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamChannel;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamInChannel;
import io.axor.runtime.StreamOutChannel;
import io.axor.runtime.impl.DefaultEventDispatcher;
import io.axor.runtime.stream.grpc.proto.AxorProto.ActorAddress;
import io.grpc.ServerBuilder;
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
            public <OUT> StreamObserver<ActorAddress> open(StreamDefinition<OUT> remote,
                                                           EventDispatcher executor,
                                                           StreamObserver<Signal> observer) {
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
                        LOG.info("{} Received: {}", selfDefinition.address(),
                                testMessage.getName());
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
        StreamChannel.StreamObserver<ActorAddress> open = channel.open(selfDefinition, executor,
                new StreamChannel.StreamObserver<>() {
                    @Override
                    public void onNext(Signal remoteSignal) {
                        LOG.info("{} received remote signal: {}", definition2.address(),
                                remoteSignal);
                    }

                    @Override
                    public void onEnd(Status status) {
                        LOG.info("{} received end stats: {}", definition2.address(), status);
                    }
                });
        open.onNext(ActorAddress.newBuilder().setName("hello").build());
        open.onNext(ActorAddress.newBuilder().setName("world").build());
        open.onEnd(StatusCode.COMPLETE.toStatus());

        Thread.sleep(1000);

    }
}
