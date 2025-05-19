package io.axor.api;

import io.axor.runtime.StreamAddress;
import org.junit.Assert;
import org.junit.Test;

public class ActorAddressTest {

    @Test
    public void testAddress() {
        ActorAddress address = ActorAddress.create("test", "localhost", 123, "name");
        StreamAddress streamAddress = address.streamAddress();
        Assert.assertEquals("test", address.system());
        Assert.assertEquals("localhost", address.host());
        Assert.assertEquals(123, address.port());
        Assert.assertEquals("name", address.name());
        Assert.assertEquals(new StreamAddress("localhost", 123, "test", "name"), streamAddress);
        Assert.assertEquals(address, ActorAddress.create(streamAddress));

        Assert.assertEquals(address, ActorAddress.create(address.toString()));

        Assert.assertThrows(IllegalArgumentException.class, () -> ActorAddress.create("test",
                "localhost", 123, "name.123"));
    }

}
