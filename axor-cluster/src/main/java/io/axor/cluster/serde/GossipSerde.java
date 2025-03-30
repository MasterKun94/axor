package io.axor.cluster.serde;

import io.axor.cluster.membership.Gossip;
import io.axor.cluster.membership.MemberClocks;
import io.axor.cluster.membership.MemberEvents;
import io.axor.runtime.MsgType;
import io.axor.runtime.impl.BuiltinSerde;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class GossipSerde implements BuiltinSerde<Gossip> {
    private final BuiltinSerde<MemberEvents> memberEventsSerde;
    private final BuiltinSerde<MemberClocks> memberClocksSerde;

    public GossipSerde(BuiltinSerde<MemberEvents> memberEventsSerde,
                       BuiltinSerde<MemberClocks> memberClocksSerde) {
        this.memberEventsSerde = memberEventsSerde;
        this.memberClocksSerde = memberClocksSerde;
    }

    @Override
    public void doSerialize(Gossip obj, DataOutput out) throws IOException {
        switch (obj) {
            case Gossip.PushedEvents(var events, var senderUid, var pull) -> {
                out.writeByte(1);
                memberEventsSerde.doSerialize(events, out);
                out.writeLong(senderUid);
                out.writeBoolean(pull);
            }
            case Gossip.Ping(var senderUid, var clocks) -> {
                out.writeByte(2);
                out.writeLong(senderUid);
                memberClocksSerde.doSerialize(clocks, out);
            }
            case Gossip.Pong(var senderUid, var pull) -> {
                out.writeByte(3);
                out.writeLong(senderUid);
                out.writeBoolean(pull);
            }
            case null, default -> throw new IllegalArgumentException("Unknown gossip type: " + obj);
        }
    }

    @Override
    public Gossip doDeserialize(DataInput in) throws IOException {
        byte b = in.readByte();
        return switch (b) {
            case 1 -> Gossip.of(memberEventsSerde.doDeserialize(in),
                    in.readLong(),
                    in.readBoolean());
            case 2 -> Gossip.ping(in.readLong(), memberClocksSerde.doDeserialize(in));
            case 3 -> Gossip.pong(in.readLong(), in.readBoolean());
            default -> throw new IllegalArgumentException("Unknown gossip type: " + b);
        };
    }

    @Override
    public MsgType<Gossip> getType() {
        return MsgType.of(Gossip.class);
    }
}
