package io.axor.cluster;

import io.axor.cluster.membership.MetaKey;
import io.axor.cluster.proto.MembershipProto.Singletons;
import io.axor.cluster.proto.MembershipProto.SubscribedTopics;

import java.util.Collections;
import java.util.List;

public class BuiltinMetaKeys {
    public static final MetaKey<String> SELF_DATACENTER = MetaKey.builder(0)
            .name("self_datacenter")
            .description("Datacenter id of this node in the cluster")
            .build("default");
    public static final MetaKey<List<String>> SELF_ROLES = MetaKey.builder(1)
            .name("self_roles")
            .description("List of roles belonging to this node in the cluster")
            .build(Collections.emptyList());
    public static final MetaKey<SubscribedTopics> SUBSCRIBED_TOPIC = MetaKey.builder(2)
            .name("subscribed_topic")
            .description("Subscribed topic descriptor of this node in the cluster")
            .build(SubscribedTopics.getDefaultInstance());
    public static final MetaKey<Singletons> SINGLETONS = MetaKey.builder(3)
            .name("singletons")
            .description("Registered singleton manager instances")
            .build(Singletons.getDefaultInstance());
}
