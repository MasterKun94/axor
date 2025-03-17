package io.masterkun.kactor.runtime.stream.grpc;

import com.google.common.net.HostAndPort;
import io.grpc.Channel;
import io.masterkun.kactor.runtime.EventDispatcher;
import io.masterkun.kactor.runtime.StreamDefinition;
import io.masterkun.kactor.runtime.StreamOutChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcStreamOutChannel<T> implements StreamOutChannel<T> {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcStreamOutChannel.class);

    private final GrpcRuntime runtime;
    private final StreamDefinition<T> selfDefinition;
    private final ChannelPool channelPool;

    public GrpcStreamOutChannel(GrpcRuntime runtime,
                                StreamDefinition<T> selfDefinition,
                                ChannelPool channelPool) {
        this.runtime = runtime;
        this.selfDefinition = selfDefinition;
        this.channelPool = channelPool;
    }

    @Override
    public <OUT> StreamObserver<OUT> open(StreamDefinition<OUT> to, EventDispatcher executor, Observer observer) {
        HostAndPort key = HostAndPort.fromParts(to.address().host(), to.address().port());
        Channel channel = channelPool.getChannel(key);
        return runtime.clientCall(channel, selfDefinition, to, executor, observer);
    }

    @Override
    public StreamDefinition<T> getSelfDefinition() {
        return selfDefinition;
    }

}
