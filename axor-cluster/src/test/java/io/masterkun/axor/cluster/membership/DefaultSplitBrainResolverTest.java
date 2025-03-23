package io.masterkun.axor.cluster.membership;

import io.masterkun.axor.cluster.LocalMemberState;
import io.masterkun.axor.cluster.MemberState;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class DefaultSplitBrainResolverTest {
    @Test
    public void testAll() {
        MetaKey<Integer> opt = MetaKey.builder(1001)
                .name("TEST")
                .description("TEST")
                .build(123);

        DefaultSplitBrainResolver resolver = new DefaultSplitBrainResolver(3, 3,
                Collections.singletonList(new DefaultSplitBrainResolver.FilterEntry<>(opt, 111)));

        assertFalse(resolver.getLocalMemberState() == LocalMemberState.UP);
        var member1 = new Member(0, MetaInfo.EMPTY, null);
        var member2 = new Member(1, MetaInfo.EMPTY, null);
        var member3 = new Member(2, MetaInfo.EMPTY, null);
        var member4 = new Member(3, MetaInfo.EMPTY, null).metaTransform(opt.upsert(111));
        resolver.onMemberStateChange(member1, MemberState.NONE, MemberState.UP);
        resolver.onMemberStateChange(member2, MemberState.NONE, MemberState.UP);
        resolver.onMemberStateChange(member3, MemberState.NONE, MemberState.UP);
        assertNotSame(LocalMemberState.UP, resolver.getLocalMemberState());
        resolver.onMemberUpdate(member1, member1 = member1.metaTransform(opt.upsert(111)));
        resolver.onMemberUpdate(member2, member2 = member2.metaTransform(opt.upsert(111)));
        assertNotSame(LocalMemberState.UP, resolver.getLocalMemberState());
        resolver.onMemberUpdate(member3, member3 = member3.metaTransform(opt.upsert(111)));
        assertSame(LocalMemberState.UP, resolver.getLocalMemberState());
        resolver.onMemberStateChange(member1, MemberState.UP, MemberState.SUSPICIOUS);
        assertSame(LocalMemberState.UP, resolver.getLocalMemberState());
        resolver.onMemberStateChange(member1, MemberState.SUSPICIOUS, MemberState.DOWN);
        assertNotSame(LocalMemberState.UP, resolver.getLocalMemberState());
        resolver.onMemberStateChange(member4, MemberState.NONE, MemberState.UP);
        assertSame(LocalMemberState.UP, resolver.getLocalMemberState());
    }


    @Test
    public void testGetLocalMemberState_Initial_NoAliveMembers() {
        DefaultSplitBrainResolver resolver = new DefaultSplitBrainResolver(3, 2);
        assertEquals(LocalMemberState.DISCONNECTED, resolver.getLocalMemberState());
    }

    @Test
    public void testGetLocalMemberState_LessThanMinRequireMembers() {
        DefaultSplitBrainResolver resolver = new DefaultSplitBrainResolver(3, 2);
        resolver.setAliveMemberCount(2);
        resolver.setInitialState(false);
        assertEquals(LocalMemberState.WEEKLY_UP, resolver.getLocalMemberState());
    }

    @Test
    public void testGetLocalMemberState_EqualToMinRequireMembers_Initial() {
        DefaultSplitBrainResolver resolver = new DefaultSplitBrainResolver(3, 2);
        resolver.setAliveMemberCount(3);
        resolver.setInitialState(true);
        assertEquals(LocalMemberState.WEEKLY_UP, resolver.getLocalMemberState());
    }

    @Test
    public void testGetLocalMemberState_GreaterThanMinRequireMembers_NotInitial() {
        DefaultSplitBrainResolver resolver = new DefaultSplitBrainResolver(3, 2);
        resolver.setAliveMemberCount(4);
        resolver.setInitialState(false);
        assertEquals(LocalMemberState.UP, resolver.getLocalMemberState());
    }
}
