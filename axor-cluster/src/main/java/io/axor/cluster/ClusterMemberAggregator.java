package io.axor.cluster;

import io.axor.cluster.ClusterEvent.MemberMetaInfoChanged;
import io.axor.cluster.ClusterEvent.MemberStateChanged;
import io.axor.cluster.membership.MetaInfo;
import io.axor.cluster.membership.MetaKey;

import java.util.List;
import java.util.function.Predicate;

public interface ClusterMemberAggregator {

    static Builder requireMemberAlive() {
        return new Builder(MemberState::isInCluster, List.of());
    }

    static Builder requireMemberServable() {
        return new Builder(MemberState::isServable, List.of());
    }

    void onEvent(ClusterEvent event);

    interface Observer {
        void onMemberAdd(ClusterMember member);

        void onMemberRemove(ClusterMember member);

        void onMemberUpdate(ClusterMember member, MetaInfo prevMeta);
    }

    interface KeyedObserver<T> {
        void onMemberAdd(ClusterMember member, T value);

        void onMemberRemove(ClusterMember member, T value);

        void onMemberUpdate(ClusterMember member, MetaInfo prevMeta, T value, T prevValue);
    }

    class Builder {
        private final Predicate<MemberState> predicate;
        private final List<String> requireRoles;

        public Builder(Predicate<MemberState> predicate, List<String> requireRoles) {
            this.predicate = predicate;
            this.requireRoles = requireRoles;
        }

        public Builder requireRoles(List<String> roles) {
            return new Builder(predicate, List.copyOf(roles));
        }

        public <T> KeyedBuilder<T> requireMetaKeyExists(MetaKey<T> key) {
            return new KeyedBuilder<>(this, key, t -> true);
        }

        public <T> KeyedBuilder<T> requireMetaKey(MetaKey<T> key, Predicate<T> filter) {
            return new KeyedBuilder<>(this, key, filter);
        }

        public ClusterMemberAggregator build(Observer observer) {
            return event -> {
                if (event instanceof MemberStateChanged(var member, var from, var to)) {
                    if (!roleExists(member.metaInfo())) {
                        return;
                    }
                    if (predicate.test(from)) {
                        if (!predicate.test(to)) {
                            observer.onMemberRemove(member);
                        }
                    } else {
                        if (predicate.test(to)) {
                            observer.onMemberAdd(member);
                        }
                    }
                } else if (event instanceof MemberMetaInfoChanged(var member, var prevMeta)) {
                    if (roleExists(member.metaInfo())) {
                        if (roleExists(prevMeta)) {
                            observer.onMemberUpdate(member, prevMeta);
                        } else {
                            observer.onMemberAdd(member);
                        }
                    } else {
                        if (roleExists(prevMeta)) {
                            observer.onMemberRemove(member);
                        }
                    }
                }
            };
        }

        private boolean roleExists(MetaInfo metaInfo) {
            return requireRoles.isEmpty() ||
                   metaInfo.get(BuiltinMetaKeys.SELF_ROLES).containsAll(requireRoles);
        }
    }

    class KeyedBuilder<T> {
        private final Builder builder;
        private final MetaKey<T> key;
        private final Predicate<T> filter;

        public KeyedBuilder(Builder builder, MetaKey<T> key, Predicate<T> filter) {
            this.builder = builder;
            this.key = key;
            this.filter = filter;
        }

        public ClusterMemberAggregator build(KeyedObserver<T> observer) {
            return builder.build(new Observer() {
                @Override
                public void onMemberAdd(ClusterMember member) {
                    MetaInfo metaInfo = member.metaInfo();
                    if (key.containsKey(metaInfo)) {
                        T t = key.get(metaInfo);
                        if (filter.test(t)) {
                            observer.onMemberAdd(member, t);
                        }
                    }
                }

                @Override
                public void onMemberRemove(ClusterMember member) {
                    MetaInfo metaInfo = member.metaInfo();
                    if (key.containsKey(metaInfo)) {
                        T t = key.get(metaInfo);
                        if (filter.test(t)) {
                            observer.onMemberRemove(member, t);
                        }
                    }
                }

                @Override
                public void onMemberUpdate(ClusterMember member, MetaInfo prevMeta) {
                    MetaInfo nowMeta = member.metaInfo();
                    T now;
                    T prev;
                    if (key.containsKey(nowMeta) && filter.test(now = key.get(nowMeta))) {
                        if (key.containsKey(prevMeta) && filter.test(prev = key.get(prevMeta))) {
                            if (!now.equals(prev)) {
                                observer.onMemberUpdate(member, prevMeta, now, prev);
                            }
                        } else {
                            observer.onMemberAdd(member, now);
                        }
                    } else {
                        if (key.containsKey(prevMeta) && filter.test(prev = key.get(prevMeta))) {
                            observer.onMemberRemove(member, prev);
                        }
                    }
                }
            });
        }
    }
}
