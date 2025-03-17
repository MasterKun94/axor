package io.masterkun.kactor.cluster.serde;

import io.masterkun.kactor.cluster.membership.MetaInfo;
import io.masterkun.kactor.cluster.membership.MetaKey;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.StatusCode;
import io.masterkun.kactor.runtime.impl.BuiltinSerde;
import io.masterkun.kactor.runtime.stream.grpc.proto.KActorProto.ActorAddress;
import io.masterkun.kactor.testkit.SerdeTestKit;
import org.junit.Test;

public class MetaInfoSerdeTest {

    @Test
    public void testSerialize() throws Exception {
        MetaInfo metaInfo = MetaInfo.EMPTY.transform(
                MetaKey.builder(1).name("int_opt").build(1).upsert(3),
                MetaKey.builder(2).name("boo_opt").build(true).upsert(false),
                MetaKey.builder(3).name("str_opt").build("test").upsert("test2")
        );
        SerdeTestKit<MetaInfo> testKit = SerdeTestKit.of(new MetaInfoSerde())
                .test(metaInfo)
                .msgType(MsgType.of(MetaInfo.class))
                .impl("builtin")
                .instanceOf(BuiltinSerde.class);

        metaInfo = MetaInfo.EMPTY.transform(
                MetaKey.builder(4).name("proto_opt").build(ActorAddress.getDefaultInstance())
                        .upsert(ActorAddress.newBuilder()
                                .setName("test")
                                .setHost("localhost")
                                .setPort(12345)
                                .build()),
                MetaKey.builder(5).name("enum_opt").build(StatusCode.CANCELLED).upsert(StatusCode.COMPLETE)
        );
        testKit.test(metaInfo);
    }

}
