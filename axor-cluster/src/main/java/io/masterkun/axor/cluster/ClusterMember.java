package io.masterkun.axor.cluster;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.Address;
import io.masterkun.axor.cluster.membership.Member;
import io.masterkun.axor.cluster.membership.MetaInfo;

import java.util.List;

public record ClusterMember(long uid, String system, Address address, MetaInfo metaInfo) {
    public static ClusterMember of(Member member) {
        ActorAddress address = member.actor().address();
        return new ClusterMember(member.uid(), address.system(), address.address(),
                member.metaInfo());
    }

    public String selfDatacenter() {
        return metaInfo.get(BuiltinMetaKeys.SELF_DATACENTER);
    }

    public List<String> selfRoles() {
        return metaInfo.get(BuiltinMetaKeys.SELF_ROLES);
    }
}
