package io.masterkun.kactor.cluster.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.masterkun.kactor.commons.config.ConfigMapper;
import org.junit.Test;

public class MembershipConfigTest {

    @Test
    public void test() {
        Config config = ConfigFactory.load().getConfig("kactor.cluster");
        MembershipConfig membershipConfig = ConfigMapper.map(config, MembershipConfig.class);
    }

}
