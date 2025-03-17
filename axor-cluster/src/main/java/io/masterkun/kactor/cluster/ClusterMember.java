package io.masterkun.kactor.cluster;

import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.api.Address;
import io.masterkun.kactor.cluster.membership.Member;
import io.masterkun.kactor.cluster.membership.MetaInfo;

import java.util.List;

public record ClusterMember(long uid, String system, Address address, MetaInfo metaInfo) {
    public static ClusterMember of(Member member) {
        ActorAddress address = member.actor().address();
        return new ClusterMember(member.uid(), address.system(), address.address(), member.metaInfo());
    }

    public String selfDatacenter() {
        return metaInfo.get(BuiltinMetaKeys.SELF_DATACENTER);
    }

    public List<String> selfRoles() {
        return metaInfo.get(BuiltinMetaKeys.SELF_ROLES);
    }
}
