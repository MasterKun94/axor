package io.axor.cluster;

import io.axor.api.Actor;
import io.axor.api.ActorAddress;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Pubsub;
import io.axor.api.impl.ActorUnsafe;
import io.axor.cluster.ClusterEvent.LocalMemberStopped;
import io.axor.cluster.membership.MetaInfo;
import io.axor.cluster.proto.MembershipProto;
import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.Unsafe;
import io.axor.runtime.stream.grpc.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.axor.cluster.BuiltinMetaKeys.SUBSCRIBED_TOPIC;
import static io.axor.cluster.proto.MembershipProto.TopicDesc;
import static io.axor.runtime.stream.grpc.StreamUtils.msgTypeToProto;
import static io.axor.runtime.stream.grpc.StreamUtils.protoToMsgType;

/**
 * A cluster-aware implementation of the {@link Pubsub} interface that manages message publishing
 * and subscribing across a distributed system. This class ensures that messages are delivered to
 * all active subscribers in the cluster, and it automatically updates the list of subscribers based
 * on cluster membership changes.
 *
 * <p>The {@code ClusterPubsub} class is designed to work with a specific topic and message type,
 * and it uses the provided {@link Cluster} instance to track and manage the cluster's membership.
 * It listens for member updates and state changes to keep the list of subscribers up-to-date,
 * ensuring that messages are only sent to currently active and compatible subscribers.</p>
 *
 * @param <T> the type of the message being published and subscribed to
 */
public class ClusterPubsub<T> implements Pubsub<T> {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterPubsub.class);

    private final String topic;
    private final MsgType<T> msgType;
    private final Cluster cluster;
    private final TopicDesc defaultTopicDesc;
    private final ActorRef<Command<T>> internalPubsubMediator;
    private final ActorRef<Command<T>> mediator;
    private final Set<ActorAddress> subscribingActors = new HashSet<>();
    private final Set<ActorAddress> unsubscribingActors = new HashSet<>();

    public ClusterPubsub(String topic, MsgType<T> msgType, Cluster cluster) {
        this.topic = topic;
        this.msgType = msgType;
        this.cluster = cluster;
        this.defaultTopicDesc = TopicDesc.newBuilder()
                .setMsgType(msgTypeToProto(msgType, cluster.system().getSerdeRegistry()))
                .build();
        String name = "cluster/pubsub/" + topic;
        Pubsub<T> internalPubsub = Pubsub.get(name, msgType, false,
                cluster.system());
        this.internalPubsubMediator = internalPubsub.mediator();
        String listenerName = name + "/listener";
        ActorRef<ClusterEvent> listener = cluster.system()
                .start(ClusterPubsubListener::new, listenerName, internalPubsub.dispatcher());
        cluster.addListener(listener);
        this.mediator = cluster.system().start(ClusterPubsubMediator::new,
                "cluster/pubsub/" + topic + "/mediator", internalPubsub.dispatcher());
    }

    @Override
    public MsgType<T> msgType() {
        return msgType;
    }

    @Override
    public ActorRef<Command<T>> mediator() {
        return mediator;
    }

    @Override
    public String toString() {
        return "ClusterPubsub[" +
               "topic=" + topic + ", " +
               "msgType=" + msgType.name() +
               "]";
    }

    private class ClusterPubsubListener extends Actor<ClusterEvent> {
        private final ClusterMemberAggregator agg;

        protected ClusterPubsubListener(ActorContext<ClusterEvent> context) {
            super(context);
            agg = ClusterMemberAggregator
                    .requireMemberServable()
                    .requireMetaKey(SUBSCRIBED_TOPIC, t -> t.containsTopic(topic))
                    .build(new ClusterMemberAggregator.KeyedObserver<>() {
                        @Override
                        public void onMemberAdd(ClusterMember member,
                                                MembershipProto.SubscribedTopics topics) {
                            updateSubscriber(null, null, member, topics);
                        }

                        @Override
                        public void onMemberRemove(ClusterMember member,
                                                   MembershipProto.SubscribedTopics topics) {
                            updateSubscriber(member, topics, null, null);
                        }

                        @Override
                        public void onMemberUpdate(ClusterMember member, MetaInfo prevMeta,
                                                   MembershipProto.SubscribedTopics topics,
                                                   MembershipProto.SubscribedTopics prevTopics) {
                            var prevMember = new ClusterMember(member.uid(), member.system(),
                                    member.address(), prevMeta);
                            updateSubscriber(prevMember, prevTopics, member, topics);
                        }
                    });
        }

        @Override
        public void onReceive(ClusterEvent event) {
            agg.onEvent(event);
            if (event instanceof LocalMemberStopped) {
                context().stop();
            }
        }

        private List<ActorAddress> getAvailableSubscribers(ClusterMember member,
                                                           MembershipProto.SubscribedTopics topics) {
            if (member == null) {
                return Collections.emptyList();
            }
            TopicDesc desc = topics.getTopicOrDefault(topic, defaultTopicDesc);
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
                            return ActorAddress.create(member.system(), member.address(),
                                    subscriber.getName());
                        } else {
                            return StreamUtils.protoToActorAddress(subscriber.getAddress());
                        }
                    })
                    .collect(Collectors.toList());
        }

        private void updateSubscriber(ClusterMember memberToRemove,
                                      MembershipProto.SubscribedTopics removedTopics,
                                      ClusterMember memberToAdd,
                                      MembershipProto.SubscribedTopics addTopics) {
            try {
                var removeList = getAvailableSubscribers(memberToRemove, removedTopics);
                var addList = getAvailableSubscribers(memberToAdd, addTopics);
                if (removeList.isEmpty() && addList.isEmpty()) {
                    return;
                }
                if (!removeList.isEmpty()) {
                    var toRemove = removeList.size() <= 8 ? removeList : new HashSet<>(removeList);
                    addList.forEach(toRemove::remove);
                    for (var addr : toRemove) {
                        var actor = cluster.system().get(addr, msgType);
                        LOG.info("ClusterPubsub[{}] unsubscribe {}", topic, actor);
                        ActorUnsafe.tellInline(internalPubsubMediator, new Unsubscribe<>(actor),
                                self());
                    }
                }
                if (!addList.isEmpty()) {
                    var toAdd = addList.size() <= 8 ? addList : new HashSet<>(addList);
                    removeList.forEach(toAdd::remove);
                    for (var addr : toAdd) {
                        var actor = cluster.system().get(addr, msgType);
                        LOG.info("ClusterPubsub[{}] subscribe {}", topic, actor);
                        ActorUnsafe.tellInline(internalPubsubMediator, new Subscribe<>(actor),
                                self());
                    }
                }
            } catch (Exception e) {
                // TODO error handle
                LOG.error("Unexpected error while updating subscriber", e);
            }
        }

        @Override
        public void onSignal(io.axor.runtime.Signal signal) {
            switch (signal) {
                case SubscribeSuccess<?>(var ignore, ActorRef<?> subscriber) -> {
                    LOG.info("ClusterPubsub[{}] subscribe [{}] success", topic, subscriber);
                    if (subscribingActors.remove(subscriber.address())) {
                        ActorUnsafe.signal(subscriber, signal, self());
                    }
                }
                case SubscribeFailed<?>(var ignore, ActorRef<?> subscriber, var e) -> {
                    LOG.error("ClusterPubsub[{}] subscribe [{}] error", topic, subscriber, e);
                    if (subscribingActors.remove(subscriber.address())) {
                        ActorUnsafe.signal(subscriber, signal, self());
                    }
                }
                case UnsubscribeSuccess<?>(var ignore, ActorRef<?> subscriber) -> {
                    LOG.info("ClusterPubsub[{}] unsubscribe [{}] success", topic, subscriber);
                    if (unsubscribingActors.remove(subscriber.address())) {
                        ActorUnsafe.signal(subscriber, signal, self());
                    }
                }
                case UnsubscribeFailed<?>(var ignore, ActorRef<?> subscriber, var e) -> {
                    LOG.error("ClusterPubsub[{}] unsubscribe [{}] error", topic, subscriber, e);
                    if (unsubscribingActors.remove(subscriber.address())) {
                        ActorUnsafe.signal(subscriber, signal, self());
                    }
                }
                case null, default -> {
                }
            }
        }

        @Override
        public MsgType<ClusterEvent> msgType() {
            return MsgType.of(ClusterEvent.class);
        }
    }

    private class ClusterPubsubMediator extends Actor<Command<T>> {
        protected ClusterPubsubMediator(ActorContext<Command<T>> context) {
            super(context);
        }

        @Override
        public void onReceive(Command<T> pMsg) {
            if (pMsg instanceof PublishToAll || pMsg instanceof SendToOne) {
                ActorUnsafe.tellInline(internalPubsubMediator, pMsg, sender());
            } else if (pMsg instanceof Subscribe<T>(var ref)) {
                doSubscribe(ref);
            } else if (pMsg instanceof Unsubscribe<T>(var ref)) {
                doUnsubscribe(ref);
            } else {
                throw new IllegalArgumentException("unsupported msg: " + pMsg);
            }
        }

        private void doSubscribe(ActorRef<? super T> ref) {
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
            subscribingActors.add(ref.address());
            cluster.updateMetaInfo(SUBSCRIBED_TOPIC.update(topics -> {
                MembershipProto.SubscribedTopics.Builder builder = topics.toBuilder();
                TopicDesc desc = builder.getTopicOrDefault(topic, defaultTopicDesc);
                if (desc.getSubscriberList().contains(subscriber)) {
                    return topics;
                }
                LOG.debug("Adding subscriber {} to topic[{}]", ref, topic);
                return builder
                        .putTopic(topic, desc.toBuilder().addSubscriber(subscriber).build())
                        .build();
            }));
        }

        private void doUnsubscribe(ActorRef<? super T> ref) {
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
        public MsgType<Command<T>> msgType() {
            return Unsafe.msgType(Command.class, List.of(msgType));
        }
    }
}
