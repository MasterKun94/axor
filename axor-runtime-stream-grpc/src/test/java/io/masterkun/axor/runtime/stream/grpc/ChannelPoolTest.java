package io.masterkun.axor.runtime.stream.grpc;

import com.google.common.net.HostAndPort;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChannelPoolTest {

    @Test
    public void testGetChannelReturnsNonNullChannel() {
        HostAndPort address = HostAndPort.fromParts("localhost", 8080);
        ChannelFactory channelFactory = mock(ChannelFactory.class);
        when(channelFactory.createChannel(anyString(), anyInt())).thenReturn(mock(ManagedChannel.class));
        try (ChannelPool channelPool = new ChannelPool(channelFactory, Duration.ofMinutes(1))) {
            Channel channel = channelPool.getChannel(address);

            assertNotNull(channel);
        }
    }

    @Test
    public void testGetChannelCachesChannels() {
        HostAndPort address = HostAndPort.fromParts("localhost", 8080);
        ChannelFactory channelFactory = mock(ChannelFactory.class);
        ManagedChannel managedChannel = mock(ManagedChannel.class);
        when(channelFactory.createChannel(anyString(), anyInt())).thenReturn(managedChannel);
        try (ChannelPool channelPool = new ChannelPool(channelFactory, Duration.ofMinutes(1))) {
            Channel firstCall = channelPool.getChannel(address);
            Channel secondCall = channelPool.getChannel(address);

            Mockito.verify(channelFactory).createChannel(anyString(), anyInt());
            assertNotNull(firstCall);
            assertNotNull(secondCall);
            assert firstCall == secondCall;
        }
    }
}
