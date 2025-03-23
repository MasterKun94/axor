package io.masterkun.axor.cluster;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.Pubsub;
import io.masterkun.axor.cluster.membership.Member;
import io.masterkun.axor.cluster.membership.MembershipListener;
import io.masterkun.axor.cluster.proto.MembershipProto;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.stream.grpc.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.masterkun.axor.cluster.BuiltinMetaKeys.SUBSCRIBED_TOPIC;
import static io.masterkun.axor.cluster.proto.MembershipProto.TopicDesc;
import static io.masterkun.axor.runtime.stream.grpc.StreamUtils.msgTypeToProto;
import static io.masterkun.axor.runtime.stream.grpc.StreamUtils.protoToMsgType;

/**
 * A cluster-aware implementation of the {@link Pubsub} interface that manages message publishing
 * and subscribing across a distributed system. This class ensures that messages are delivered to
 * all active subscribers in the cluster, and it automatically updates the list of subscribers based
 * on cluster membership changes.
 *
 * <p>The {@code ClusterPubsub} class is designed to work with a specific topic and message type,
 * and it uses the
 * provided {@link Cluster} instance to track and manage the cluster's membership. It listens for
 * member updates and state changes to keep the list of subscribers up-to-date, ensuring that
 * messages are only sent to currently active and compatible subscribers.</p>
 *
 * @param <T> the type of the message being published and subscribed to
 */
public class ClusterPubsub<T> implements Pubsub<T> {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterPubsub.class);

    private final String topic;
    private final MsgType<T> msgType;
    private final Cluster cluster;
    private final TopicDesc defaultTopicDesc;
    private final Pubsub<T> internalPubsub;

    public ClusterPubsub(String topic, MsgType<T> msgType, Cluster cluster) {
        this.topic = topic;
        this.msgType = msgType;
        this.cluster = cluster;
        this.defaultTopicDesc = TopicDesc.newBuilder()
                .setMsgType(msgTypeToProto(msgType, cluster.system().getSerdeRegistry()))
                .build();
        this.internalPubsub = Pubsub.create(cluster.system(), msgType, false);
        cluster.addListener(new MembershipListener() {
            @Override
            public void onMemberUpdate(Member from, Member to) {
                if (SUBSCRIBED_TOPIC.metaEquals(from.metaInfo(), to.metaInfo())) {
                    return;
                }
                updateSubscriber(from, to);
            }

            @Override
            public void onMemberStateChange(Member member, MemberState from, MemberState to) {
                if (from.ALIVE) {
                    if (to.ALIVE) {
                        return;
                    }
                    updateSubscriber(member, null);
                } else if (to.ALIVE) {
                    updateSubscriber(null, member);
                }
            }
        });
    }

    private List<ActorAddress> getAvailableSubscribers(Member member) {
        if (member == null) {
            return Collections.emptyList();
        }
        TopicDesc desc = SUBSCRIBED_TOPIC.get(member.metaInfo())
                .getTopicOrDefault(topic, defaultTopicDesc);
        if (desc.getSubscriberCount() == 0) {
            return Collections.emptyList();
        }
        if (!desc.getMsgType().equals(defaultTopicDesc.getMsgType())) {
            SerdeRegistry registry = cluster.system().getSerdeRegistry();
            MsgType<?> remoteType = protoToMsgType(desc.getMsgType(), registry);
            if (!remoteType.equals(msgType)) {
                LOG.warn("Topic {} msgType mismatch between nodes, remote: [{}], local: [{}]",
                        topic, remoteType, msgType);
                return Collections.emptyList();
            }
        }
        return desc.getSubscriberList().stream()
                .map(subscriber -> {
                    if (subscriber.hasName()) {
                        var addr = member.actor().address();
                        return ActorAddress.create(addr.system(), addr.address(),
                                subscriber.getName());
                    } else {
                        return StreamUtils.protoToActorAddress(subscriber.getAddress());
                    }
                })
                .collect(Collectors.toList());
    }

    private void updateSubscriber(Member memberToRemove, Member memberToAdd) {
        try {
            var removeList = getAvailableSubscribers(memberToRemove);
            var addList = getAvailableSubscribers(memberToAdd);
            if (removeList.isEmpty() && addList.isEmpty()) {
                return;
            }
            Set<ActorAddress> removeSet = removeList.isEmpty() ? Set.of() :
                    new HashSet<>(removeList);
            Set<ActorAddress> addSet = addList.isEmpty() ? Set.of() : new HashSet<>(addList);
            addList.forEach(removeSet::remove);
            removeList.forEach(addSet::remove);
            for (var addr : removeSet) {
                var actor = cluster.system().get(addr, msgType);
                LOG.info("Notify unsubscribe {} from topic[{}]", actor, topic);
                internalPubsub.unsubscribe(actor);
            }
            for (var addr : addSet) {
                var actor = cluster.system().get(addr, msgType);
                LOG.info("Notify subscribe {} to topic[{}]", actor, topic);
                internalPubsub.subscribe(actor);
            }
        } catch (Exception e) {
            // TODO error handle
            LOG.error("Unexpected error while updating subscriber", e);
        }
    }

    @Override
    public void publishToAll(T msg, ActorRef<?> sender) {
        internalPubsub.publishToAll(msg, sender);
    }

    @Override
    public void sendToOne(T msg, ActorRef<?> sender) {
        internalPubsub.sendToOne(msg, sender);
    }

    @Override
    public void subscribe(ActorRef<? super T> ref) {
        MembershipProto.Subscriber subscriber;
        if (cluster.system().isLocal(ref)) {
            subscriber = MembershipProto.Subscriber.newBuilder()
                    .setName(ref.address().name())
                    .build();
        } else {
            subscriber = MembershipProto.Subscriber.newBuilder()
                    .setAddress(StreamUtils.actorAddressToProto(ref.address()))
                    .build();
        }
        cluster.updateMetaInfo(SUBSCRIBED_TOPIC.update(topics -> {
            MembershipProto.SubscribedTopics.Builder builder = topics.toBuilder();
            TopicDesc desc = builder.getTopicOrDefault(topic, defaultTopicDesc);
            if (desc.getSubscriberList().contains(subscriber)) {
                return topics;
            }
            LOG.info("Adding subscriber {} to topic[{}]", ref, topic);
            return builder
                    .putTopic(topic, desc.toBuilder().addSubscriber(subscriber).build())
                    .build();
        }));
    }

    @Override
    public void unsubscribe(ActorRef<? super T> ref) {
        MembershipProto.Subscriber subscriber;
        if (cluster.system().isLocal(ref)) {
            subscriber = MembershipProto.Subscriber.newBuilder()
                    .setName(ref.address().name())
                    .build();
        } else {
            subscriber = MembershipProto.Subscriber.newBuilder()
                    .setAddress(StreamUtils.actorAddressToProto(ref.address()))
                    .build();
        }
        cluster.updateMetaInfo(SUBSCRIBED_TOPIC.update(topics -> {
            MembershipProto.SubscribedTopics.Builder builder = topics.toBuilder();
            TopicDesc desc = builder.getTopicOrDefault(topic, defaultTopicDesc);
            int index = desc.getSubscriberList().indexOf(subscriber);
            if (index == -1) {
                return topics;
            }
            LOG.info("Removing subscriber {} from topic[{}]", ref, topic);
            return builder
                    .putTopic(topic, desc.toBuilder().removeSubscriber(index).build())
                    .build();
        }));
    }

    @Override
    public MsgType<T> msgType() {
        return msgType;
    }
}
