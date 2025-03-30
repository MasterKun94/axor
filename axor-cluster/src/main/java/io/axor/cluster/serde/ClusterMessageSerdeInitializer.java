package io.axor.cluster.serde;

import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.api.ActorSystemSerdeInitializer;
import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.impl.BuiltinSerdeFactory;

public class ClusterMessageSerdeInitializer extends ActorSystemSerdeInitializer<BuiltinSerdeFactory> {

    @Override
    protected void initialize(ActorSystem actorSystem, BuiltinSerdeFactory serdeFactory,
                              SerdeRegistry registry) {
        var metaInfoSerde = new MetaInfoSerde();
        var memberSerde = new MemberSerde(metaInfoSerde,
                serdeFactory.create(MsgType.of(ActorRef.class)));
        var vectorClockSerde = new VectorClockSerde();
        var memberEventSerde = new MemberEventSerde(memberSerde, vectorClockSerde);
        var memberEventsSerde = new MemberEventsSerde(memberEventSerde);
        var memberClockSerde = new MemberClockSerde();
        var memberClocksSerde = new MemberClocksSerde(memberClockSerde);
        var gossipSerde = new GossipSerde(memberEventsSerde, memberClocksSerde);
        var membershipMessageSerde = new MembershipMessageSerde(gossipSerde);

        serdeFactory.register(metaInfoSerde);
        serdeFactory.register(memberSerde);
        serdeFactory.register(memberEventSerde);
        serdeFactory.register(memberEventsSerde);
        serdeFactory.register(memberClockSerde);
        serdeFactory.register(memberClocksSerde);
        serdeFactory.register(vectorClockSerde);
        serdeFactory.register(gossipSerde);
        serdeFactory.register(membershipMessageSerde);
    }

    @Override
    protected Class<BuiltinSerdeFactory> getFactoryClass() {
        return BuiltinSerdeFactory.class;
    }

    @Override
    public int priority() {
        return 1;
    }
}
