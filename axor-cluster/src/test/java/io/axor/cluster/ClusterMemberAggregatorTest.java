package io.axor.cluster;

import io.axor.api.Address;
import io.axor.cluster.ClusterEvent.MemberMetaInfoChanged;
import io.axor.cluster.ClusterEvent.MemberStateChanged;
import io.axor.cluster.membership.MetaInfo;
import io.axor.cluster.membership.MetaKey;
import io.axor.cluster.membership.MetaKeys;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class ClusterMemberAggregatorTest {
    private static final MetaKey<String> testKey = MetaKeys.create(123, "test", "test", "Hello");
    private static final ClusterMember member = new ClusterMember(1, "test", new Address(
            "localhost", 1000), MetaInfo.EMPTY);
    private static final ClusterMember memberWithRole1 = new ClusterMember(2, "test", new Address(
            "localhost", 1001),
            MetaInfo.EMPTY.transform(BuiltinMetaKeys.SELF_ROLES.upsert(Set.of("role1"))));
    private static final ClusterMember memberWithRole12 = new ClusterMember(3, "test", new Address(
            "localhost", 1002),
            MetaInfo.EMPTY.transform(BuiltinMetaKeys.SELF_ROLES.upsert(Set.of("role1", "role2"))));
    private static final ClusterMember memberWithRole12WithKey = new ClusterMember(4, "test",
            new Address(
                    "localhost", 1003),
            MetaInfo.EMPTY.transform(
                    BuiltinMetaKeys.SELF_ROLES.upsert(Set.of("role1", "role2")),
                    testKey.upsert("World")));

    @Test
    public void testOnEvent_MemberStateChangedFromServableToDead() {
        ClusterMemberAggregator.Observer observer = mock(ClusterMemberAggregator.Observer.class);
        ClusterMemberAggregator aggregator =
                ClusterMemberAggregator.requireMemberServable().build(observer);

        aggregator.onEvent(new MemberStateChanged(member, MemberState.UP, MemberState.DOWN));
        aggregator.onEvent(new MemberStateChanged(member, MemberState.SUSPICIOUS,
                MemberState.DOWN));
        aggregator.onEvent(new MemberStateChanged(member, MemberState.UP, MemberState.LEFT));
        aggregator.onEvent(new MemberStateChanged(member, MemberState.SUSPICIOUS,
                MemberState.LEFT));
        verify(observer, times(4)).onMemberRemove(member);
        reset(observer);

        aggregator.onEvent(new MemberStateChanged(member, MemberState.UP, MemberState.SUSPICIOUS));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.SUSPICIOUS,
                MemberState.UP));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.DOWN, MemberState.LEFT));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.LEFT,
                MemberState.DOWN));
        verifyNoInteractions(observer);
    }

    @Test
    public void testOnEvent_MemberStateChangedFromDeadToServable() {
        ClusterMemberAggregator.Observer observer = mock(ClusterMemberAggregator.Observer.class);
        ClusterMemberAggregator aggregator =
                ClusterMemberAggregator.requireMemberServable().build(observer);

        aggregator.onEvent(new MemberStateChanged(member, MemberState.DOWN, MemberState.UP));
        aggregator.onEvent(new MemberStateChanged(member, MemberState.DOWN,
                MemberState.SUSPICIOUS));
        aggregator.onEvent(new MemberStateChanged(member, MemberState.LEFT, MemberState.UP));
        aggregator.onEvent(new MemberStateChanged(member, MemberState.LEFT,
                MemberState.SUSPICIOUS));
        verify(observer, times(4)).onMemberAdd(member);
        reset(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.UP, MemberState.SUSPICIOUS));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.SUSPICIOUS,
                MemberState.UP));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.DOWN, MemberState.LEFT));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.LEFT,
                MemberState.DOWN));
        verifyNoInteractions(observer);
    }

    @Test
    public void testOnEvent_MemberMetaInfoChangedWithRole() {
        ClusterMemberAggregator.Observer observer = mock(ClusterMemberAggregator.Observer.class);
        ClusterMemberAggregator aggregator =
                ClusterMemberAggregator.requireMemberServable()
                        .requireRoles(List.of("role1", "role2"))
                        .build(observer);

        aggregator.onEvent(new MemberStateChanged(memberWithRole12, MemberState.UP,
                MemberState.DOWN));
        aggregator.onEvent(new MemberStateChanged(memberWithRole12, MemberState.SUSPICIOUS,
                MemberState.DOWN));
        aggregator.onEvent(new MemberStateChanged(memberWithRole12, MemberState.UP,
                MemberState.LEFT));
        aggregator.onEvent(new MemberStateChanged(memberWithRole12, MemberState.SUSPICIOUS,
                MemberState.LEFT));
        verify(observer, times(4)).onMemberRemove(memberWithRole12);

        reset(observer);
        aggregator.onEvent(new MemberStateChanged(memberWithRole1, MemberState.UP,
                MemberState.DOWN));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(memberWithRole1, MemberState.SUSPICIOUS,
                MemberState.DOWN));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(memberWithRole1, MemberState.UP,
                MemberState.LEFT));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(memberWithRole1, MemberState.SUSPICIOUS,
                MemberState.LEFT));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.UP, MemberState.DOWN));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.SUSPICIOUS,
                MemberState.DOWN));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.UP, MemberState.LEFT));
        verifyNoInteractions(observer);
        aggregator.onEvent(new MemberStateChanged(member, MemberState.SUSPICIOUS,
                MemberState.LEFT));
        verifyNoInteractions(observer);

        aggregator =
                ClusterMemberAggregator.requireMemberServable()
                        .requireRoles(List.of("role1"))
                        .build(observer);

        aggregator.onEvent(new MemberStateChanged(memberWithRole1, MemberState.UP,
                MemberState.DOWN));
        aggregator.onEvent(new MemberStateChanged(memberWithRole1, MemberState.SUSPICIOUS,
                MemberState.DOWN));
        aggregator.onEvent(new MemberStateChanged(memberWithRole1, MemberState.UP,
                MemberState.LEFT));
        aggregator.onEvent(new MemberStateChanged(memberWithRole1, MemberState.SUSPICIOUS,
                MemberState.LEFT));
        verify(observer, times(4)).onMemberRemove(memberWithRole1);

    }

    @Test
    public void testOnEvent_MemberMetaInfoChangedWithRoleAdded() {
        ClusterMemberAggregator.Observer observer = mock(ClusterMemberAggregator.Observer.class);
        ClusterMemberAggregator aggregator =
                ClusterMemberAggregator.requireMemberServable()
                        .requireRoles(List.of("role1", "role2"))
                        .build(observer);
        aggregator.onEvent(new MemberMetaInfoChanged(memberWithRole12, memberWithRole1.metaInfo()));
        verify(observer).onMemberAdd(memberWithRole12);
    }

    @Test
    public void testOnEvent_MemberMetaInfoChangedWithRoleRemoved() {
        ClusterMemberAggregator.Observer observer = mock(ClusterMemberAggregator.Observer.class);
        ClusterMemberAggregator aggregator =
                ClusterMemberAggregator.requireMemberServable()
                        .requireRoles(List.of("role1", "role2"))
                        .build(observer);
        aggregator.onEvent(new MemberMetaInfoChanged(memberWithRole1, memberWithRole12.metaInfo()));
        verify(observer).onMemberRemove(memberWithRole1);
    }

    @Test
    public void testOnEvent_MemberMetaInfoChangedWithRoleUpdated() {
        ClusterMemberAggregator.Observer observer = mock(ClusterMemberAggregator.Observer.class);
        ClusterMemberAggregator aggregator =
                ClusterMemberAggregator.requireMemberServable()
                        .requireRoles(List.of("role1", "role2"))
                        .build(observer);
        MetaInfo previousMetaInfo = memberWithRole12.metaInfo().transform(testKey.upsert("HiHi"));
        aggregator.onEvent(new MemberMetaInfoChanged(memberWithRole12, previousMetaInfo));
        verify(observer).onMemberUpdate(memberWithRole12, previousMetaInfo);
    }


    @Test
    public void testOnEvent_MemberMetaInfoChangedWithKey() {
        ClusterMemberAggregator.KeyedObserver<String> observer =
                mock(ClusterMemberAggregator.KeyedObserver.class);
        ClusterMemberAggregator aggregator =
                ClusterMemberAggregator.requireMemberServable()
                        .requireRoles(List.of("role1", "role2"))
                        .requireMetaKeyExists(testKey)
                        .build(observer);
        MetaInfo previousMetaInfo = memberWithRole12.metaInfo().transform(testKey.upsert("HiHi"));

        aggregator.onEvent(new MemberMetaInfoChanged(memberWithRole12WithKey,
                memberWithRole12.metaInfo()));
        verify(observer).onMemberAdd(memberWithRole12WithKey, "World");
        reset(observer);
        aggregator.onEvent(new MemberMetaInfoChanged(memberWithRole12, previousMetaInfo));
        verify(observer).onMemberRemove(memberWithRole12, "HiHi");
        reset(observer);
        aggregator.onEvent(new MemberMetaInfoChanged(memberWithRole12WithKey, previousMetaInfo));
        verify(observer).onMemberUpdate(memberWithRole12WithKey, previousMetaInfo, "World", "HiHi");
        reset(observer);


    }
}
