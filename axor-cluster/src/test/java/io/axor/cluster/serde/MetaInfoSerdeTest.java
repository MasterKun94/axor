package io.axor.cluster.serde;

import io.axor.cluster.membership.MetaInfo;
import io.axor.cluster.membership.MetaKey;
import io.axor.runtime.MsgType;
import io.axor.runtime.StatusCode;
import io.axor.runtime.impl.BuiltinSerde;
import io.axor.runtime.stream.grpc.proto.AxorProto.ActorAddress;
import io.axor.testkit.SerdeTestKit;
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
