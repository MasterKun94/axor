package io.axor.cluster.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.axor.commons.config.ConfigMapper;
import org.junit.Test;

public class MembershipConfigTest {

    @Test
    public void test() {
        Config config = ConfigFactory.load().getConfig("axor.cluster.default-cluster");
        MembershipConfig membershipConfig = ConfigMapper.map(config, MembershipConfig.class);
    }

}
