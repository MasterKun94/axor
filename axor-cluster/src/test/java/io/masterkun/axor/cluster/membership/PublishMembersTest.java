package io.masterkun.axor.cluster.membership;

import com.google.common.collect.Iterables;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.cluster.MemberState;
import io.masterkun.axor.cluster.config.MemberManageConfig;
import io.masterkun.axor.cluster.membership.MemberManager.PublishMembers;
import io.masterkun.axor.commons.collection.LongObjectHashMap;
import io.masterkun.axor.commons.collection.LongObjectMap;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PublishMembersTest {

    @Test
    public void testIteratorWithNoMembers() {
        MemberManageConfig config = new MemberManageConfig(0.5, 1);

        LongObjectMap<MemberManager.MemberHolder> allMembers = new LongObjectHashMap<>();
        PublishMembers publishMembers = new PublishMembers(config, 1, allMembers);
        publishMembers.upMemberChanged();

        List<Member> members = new ArrayList<>();
        for (Member member : publishMembers) {
            members.add(member);
        }

        assertEquals(0, members.size());
    }

    @Test
    public void testIteratorWithOneUpMember() {
        MemberManageConfig config = new MemberManageConfig(0.5, 1);

        LongObjectMap<MemberManager.MemberHolder> allMembers = new LongObjectHashMap<>();
        long selfUid = 1;
        long otherUid = 2;
        ActorRef<Gossip> actor = Mockito.mock(ActorRefRich.class);
        Member member = new Member(otherUid, MetaInfo.EMPTY, actor);
        MemberManager.MemberHolder holder = new MemberManager.MemberHolder();
        holder.member = member;
        holder.state = MemberState.UP;

        allMembers.put(otherUid, holder);

        PublishMembers publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();

        List<Member> members = new ArrayList<>();
        for (Member m : publishMembers) {
            members.add(m);
        }

        assertEquals(1, members.size());
        assertEquals(otherUid, members.get(0).uid());
    }

    @Test
    public void testIteratorWithMultipleUpMembers() {
        MemberManageConfig config = new MemberManageConfig(0.5, 2);

        LongObjectMap<MemberManager.MemberHolder> allMembers = new LongObjectHashMap<>();
        long selfUid = 1;
        long otherUid1 = 2;
        long otherUid2 = 3;
        ActorRef<Gossip> actor1 = Mockito.mock(ActorRefRich.class);
        ActorRef<Gossip> actor2 = Mockito.mock(ActorRefRich.class);
        Member member1 = new Member(otherUid1, MetaInfo.EMPTY, actor1);
        Member member2 = new Member(otherUid2, MetaInfo.EMPTY, actor2);
        MemberManager.MemberHolder holder1 = new MemberManager.MemberHolder();
        MemberManager.MemberHolder holder2 = new MemberManager.MemberHolder();
        holder1.member = member1;
        holder1.state = MemberState.UP;
        holder2.member = member2;
        holder2.state = MemberState.UP;

        allMembers.put(otherUid1, holder1);
        allMembers.put(otherUid2, holder2);

        PublishMembers publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();

        List<Member> members = new ArrayList<>();
        for (Member m : publishMembers) {
            members.add(m);
        }

        assertEquals(2, members.size());
        members.sort(Comparator.comparingLong(Member::uid));
        assertEquals(otherUid1, members.get(0).uid());
        assertEquals(otherUid2, members.get(1).uid());
    }

    @Test
    public void testIteratorWithMixedMemberStates() {
        MemberManageConfig config = new MemberManageConfig(0.5, 2);

        LongObjectMap<MemberManager.MemberHolder> allMembers = new LongObjectHashMap<>();
        long selfUid = 1;
        long otherUid1 = 2;
        long otherUid2 = 3;
        long otherUid3 = 4;
        ActorRef<Gossip> actor1 = Mockito.mock(ActorRefRich.class);
        ActorRef<Gossip> actor2 = Mockito.mock(ActorRefRich.class);
        ActorRef<Gossip> actor3 = Mockito.mock(ActorRefRich.class);
        Member member1 = new Member(otherUid1, MetaInfo.EMPTY, actor1);
        Member member2 = new Member(otherUid2, MetaInfo.EMPTY, actor2);
        Member member3 = new Member(otherUid3, MetaInfo.EMPTY, actor3);
        MemberManager.MemberHolder holder1 = new MemberManager.MemberHolder();
        MemberManager.MemberHolder holder2 = new MemberManager.MemberHolder();
        MemberManager.MemberHolder holder3 = new MemberManager.MemberHolder();
        holder1.member = member1;
        holder1.state = MemberState.UP;
        holder2.member = member2;
        holder2.state = MemberState.DOWN;
        holder3.member = member3;
        holder3.state = MemberState.UP;

        allMembers.put(otherUid1, holder1);
        allMembers.put(otherUid2, holder2);
        allMembers.put(otherUid3, holder3);

        PublishMembers publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();

        List<Member> members = new ArrayList<>();
        for (Member m : publishMembers) {
            members.add(m);
        }

        assertEquals(2, members.size());
        members.sort(Comparator.comparingLong(Member::uid));
        assertEquals(otherUid1, members.get(0).uid());
        assertEquals(otherUid3, members.get(1).uid());
    }


    /**
     * Tests the publish rate of members based on the configuration.
     * The method sets up a scenario with multiple members, each in an UP state, and verifies that
     * the number of members to be published matches the expected value based on different configurations.
     * It checks the behavior of the PublishMembers class when the member manage configuration changes,
     * specifically focusing on how the publish rate (number of members to publish) is affected by
     * the configuration's threshold and maxPublish parameters.
     */
    @Test
    public void testPublishRate() {

        LongObjectMap<MemberManager.MemberHolder> allMembers = new LongObjectHashMap<>();
        long selfUid = 1;
        long otherUid1 = 2;
        long otherUid2 = 3;
        long otherUid3 = 4;
        long otherUid4 = 5;
        Member member1 = new Member(otherUid1, MetaInfo.EMPTY, Mockito.mock(ActorRefRich.class));
        Member member2 = new Member(otherUid2, MetaInfo.EMPTY, Mockito.mock(ActorRefRich.class));
        Member member3 = new Member(otherUid3, MetaInfo.EMPTY, Mockito.mock(ActorRefRich.class));
        Member member4 = new Member(otherUid4, MetaInfo.EMPTY, Mockito.mock(ActorRefRich.class));
        MemberManager.MemberHolder holder1 = new MemberManager.MemberHolder();
        MemberManager.MemberHolder holder2 = new MemberManager.MemberHolder();
        MemberManager.MemberHolder holder3 = new MemberManager.MemberHolder();
        MemberManager.MemberHolder holder4 = new MemberManager.MemberHolder();
        holder1.member = member1;
        holder1.state = MemberState.UP;
        holder2.member = member2;
        holder2.state = MemberState.UP;
        holder3.member = member3;
        holder3.state = MemberState.UP;
        holder4.member = member4;
        holder4.state = MemberState.UP;

        allMembers.put(otherUid1, holder1);
        allMembers.put(otherUid2, holder2);
        allMembers.put(otherUid3, holder3);
        allMembers.put(otherUid4, holder4);

        MemberManageConfig config = new MemberManageConfig(0.5, 2);
        PublishMembers publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();
        assertEquals(2, Iterables.size(publishMembers));

        config = new MemberManageConfig(0.5, 1);
        publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();
        assertEquals(2, Iterables.size(publishMembers));

        config = new MemberManageConfig(0.5, 3);
        publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();
        assertEquals(3, Iterables.size(publishMembers));

        config = new MemberManageConfig(0.6, 2);
        publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();
        assertEquals(2, Iterables.size(publishMembers));

        config = new MemberManageConfig(0.7, 2);
        publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();
        assertEquals(3, Iterables.size(publishMembers));

        config = new MemberManageConfig(0.8, 2);
        publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();
        assertEquals(3, Iterables.size(publishMembers));

        config = new MemberManageConfig(0.9, 2);
        publishMembers = new PublishMembers(config, selfUid, allMembers);
        publishMembers.upMemberChanged();
        assertEquals(4, Iterables.size(publishMembers));
    }
}
