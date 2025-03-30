package io.axor.cluster.serde;

import io.axor.cluster.membership.Gossip;
import io.axor.cluster.membership.MembershipMessage;
import io.axor.runtime.MsgType;
import io.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MembershipMessageSerde implements BuiltinSerde<MembershipMessage> {
    private final GossipSerde gossipSerde;

    public MembershipMessageSerde(GossipSerde gossipSerde) {
        this.gossipSerde = gossipSerde;
    }

    @Override
    public void doSerialize(MembershipMessage obj, DataOutput out) throws IOException {
        gossipSerde.doSerialize((Gossip) obj, out);
    }

    @Override
    public MembershipMessage doDeserialize(DataInput in) throws IOException {
        return gossipSerde.doDeserialize(in);
    }

    @Override
    public MsgType<MembershipMessage> getType() {
        return MsgType.of(MembershipMessage.class);
    }
}
