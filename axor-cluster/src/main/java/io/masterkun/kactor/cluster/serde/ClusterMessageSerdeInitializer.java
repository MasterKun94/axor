package io.masterkun.kactor.cluster.serde;

import io.masterkun.kactor.api.ActorRef;
import io.masterkun.kactor.api.ActorSystem;
import io.masterkun.kactor.api.ActorSystemSerdeInitializer;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.runtime.impl.BuiltinSerdeFactory;

public class ClusterMessageSerdeInitializer extends ActorSystemSerdeInitializer<BuiltinSerdeFactory> {

    @Override
    protected void initialize(ActorSystem actorSystem, BuiltinSerdeFactory serdeFactory, SerdeRegistry registry) {
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
