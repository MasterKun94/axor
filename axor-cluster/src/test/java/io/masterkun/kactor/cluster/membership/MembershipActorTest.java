package io.masterkun.kactor.cluster.membership;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.api.ActorSystem;
import io.masterkun.kactor.cluster.LocalMemberState;
import io.masterkun.kactor.cluster.MemberState;
import io.masterkun.kactor.cluster.config.FailureDetectConfig;
import io.masterkun.kactor.cluster.config.JoinConfig;
import io.masterkun.kactor.cluster.config.LeaveConfig;
import io.masterkun.kactor.cluster.config.MemberManageConfig;
import io.masterkun.kactor.cluster.config.MembershipConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class MembershipActorTest {

    private static final MembershipConfig config = new MembershipConfig(
            new JoinConfig(
                    false,
                    Duration.ofSeconds(10),
                    "//localhost:10001"),
            new LeaveConfig(
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(30)),
            new MemberManageConfig(
                    0.8,
                    10
            ),
            new FailureDetectConfig(
                    true,
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(20),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(120)
            )
    );

    private static ActorSystem createActorSystem(String name, int port) {
        Config config = ConfigFactory.load(ConfigFactory.parseString("kactor.network.bind.port = " + port))
                .resolve();
        return ActorSystem.create(name, config);
    }

    private static void start(int port, Logger log) {
        ActorSystem system = createActorSystem("test", port);
        ActorRef<MembershipMessage> actor = system.start(
                actorContext ->
                        new MembershipActor(actorContext, config, new DefaultSplitBrainResolver(2, 2)),
                "membership"
        );
        actor.tell(new MembershipMessage.AddListener(new MembershipListener() {
            @Override
            public void onLocalStateChange(LocalMemberState currentState) {
                log.info("onLocalStateChange({})", currentState);
            }

            @Override
            public void onMemberStateChange(Member member, MemberState from, MemberState to) {
                log.info("onMemberStateChange(member={}, from={}, to={})", member, from, to);
            }

            @Override
            public void onMemberUpdate(Member from, Member to) {
                log.info("onMemberUpdate(from={}, to={})", from, to);
            }
        }, false));
        actor.tell(MembershipMessage.JOIN);
    }

    public static class Node1 {
        public static void main(String[] args) {
            start(10001, LoggerFactory.getLogger(Node1.class));
        }
    }


    public static class Node2 {
        public static void main(String[] args) {
            start(10002, LoggerFactory.getLogger(Node2.class));
        }
    }


    public static class Node3 {
        public static void main(String[] args) {
            start(10003, LoggerFactory.getLogger(Node3.class));
        }
    }
}
