package io.masterkun.kactor.cluster.serde;

import io.masterkun.kactor.cluster.membership.Gossip;
import io.masterkun.kactor.cluster.membership.MembershipMessage;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.impl.BuiltinSerde;

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
