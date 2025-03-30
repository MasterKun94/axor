package io.axor.cluster.membership;

import io.axor.api.ActorRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GossipTest {

    @Test
    public void addEvents() {
        Member member = new Member(1, MetaInfo.EMPTY, ActorRef.noSender());
        VectorClock vectorClock = VectorClock.wrap(1, 1, 2, 1);
        MemberEvent event = new MemberEvent(member, MemberAction.UPDATE, vectorClock);
        Gossip gossip = Gossip.of(event, 1);
        assertEquals(1, gossip.events().size());
    }
}
