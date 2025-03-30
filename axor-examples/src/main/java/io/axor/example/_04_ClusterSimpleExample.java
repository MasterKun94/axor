package io.axor.example;

import com.typesafe.config.Config;
import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorSystem;
import io.axor.api.MessageUtils;
import io.axor.cluster.Cluster;
import io.axor.cluster.ClusterEvent;
import io.axor.cluster.ClusterEvent.LocalStateChange;
import io.axor.cluster.ClusterEvent.MemberMetaInfoChanged;
import io.axor.cluster.ClusterEvent.MemberStateChanged;
import io.axor.cluster.membership.MetaKey;
import io.axor.cluster.membership.MetaKeys;
import io.axor.example.proto.ExampleProto.CustomMessage;
import io.axor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;

/**
 * This class provides a simple example of how to set up and manage a cluster with multiple nodes.
 * Each node in the cluster updates its metadata at regular intervals, and a listener is used to
 * monitor and log changes in the cluster's state and metadata.
 */
public class _04_ClusterSimpleExample {
    public static final MetaKeys.IntMetaKey MEMBER_VERSION = MetaKey.builder(101)
            .name("member_version")
            .description("Member Version")
            .build(0);
    public static final MetaKey<CustomMessage> CUSTOM_META = MetaKey.builder(102)
            .name("some_custom_meta")
            .description("Some Custom Meta")
            .build(CustomMessage.getDefaultInstance());


    private static void startNode(int port) {
        Config config = load(parseString(("""
                axor.network.bind {
                    port = %d
                    host = "localhost"
                }
                axor.cluster.default-cluster {
                    join.seeds = ["localhost:1101"]
                }
                """.formatted(port)))).resolve();
        ActorSystem system = ActorSystem.create("example", config);
        Cluster cluster = Cluster.get(system);
        cluster.addListener(system.start(MemberListener::new, "memberListener"));
        system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
            cluster.updateMetaInfo(MEMBER_VERSION.update(i -> i + 1));
        }, 3, 3, TimeUnit.SECONDS);
        system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
            cluster.updateMetaInfo(CUSTOM_META.upsert(CustomMessage.newBuilder()
                    .setId(ThreadLocalRandom.current().nextInt())
                    .setContent(UUID.randomUUID().toString())
                    .build()));
        }, 5, 5, TimeUnit.SECONDS);
    }

    public static class Node1 {
        public static void main(String[] args) {
            startNode(1101);
        }
    }

    public static class Node2 {
        public static void main(String[] args) {
            startNode(1102);
        }
    }

    public static class Node3 {
        public static void main(String[] args) {
            startNode(1103);
        }
    }

    public static class MemberListener extends Actor<ClusterEvent> {
        private static final Logger LOG = LoggerFactory.getLogger(MemberListener.class);

        public MemberListener(ActorContext<ClusterEvent> context) {
            super(context);
        }

        @Override
        public void onReceive(ClusterEvent event) {
            if (event instanceof LocalStateChange(var state)) {
                LOG.info("Local member state changed to: {}", state);
            } else if (event instanceof MemberStateChanged(var member, var from, var to)) {
                LOG.info("{} state changed from {} to {}", member, from, to);
            } else if (event instanceof MemberMetaInfoChanged(var member, var prevMeta)) {
                int nowVersion = MEMBER_VERSION.getValue(member.metaInfo());
                int prevVersion = MEMBER_VERSION.getValue(prevMeta);
                CustomMessage nowMsg = CUSTOM_META.get(member.metaInfo());
                CustomMessage prevMsg = CUSTOM_META.get(prevMeta);
                if (nowVersion != prevVersion) {
                    LOG.info("{} meta info changed, version updated to {}", member, nowVersion);
                } else if (!nowMsg.equals(prevMsg)) {
                    LOG.info("{} meta info changed, custom message updated to [{}]", member,
                            MessageUtils.loggable(nowMsg));
                } else {
                    LOG.info("{} meta info changed", member);
                }
            }
        }

        @Override
        public MsgType<ClusterEvent> msgType() {
            return MsgType.of(ClusterEvent.class);
        }
    }
}
