package io.axor.runtime.stream.grpc;

import com.google.common.net.HostAndPort;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamOutChannel;
import io.grpc.Channel;
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
    public <OUT> StreamObserver<OUT> open(StreamDefinition<OUT> to, EventDispatcher executor,
                                          Observer observer) {
        HostAndPort key = HostAndPort.fromParts(to.address().host(), to.address().port());
        Channel channel = channelPool.getChannel(key);
        return runtime.clientCall(channel, selfDefinition, to, executor, observer);
    }

    @Override
    public StreamDefinition<T> getSelfDefinition() {
        return selfDefinition;
    }

}
