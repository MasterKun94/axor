package io.axor.cluster;

import io.axor.api.ActorAddress;
import io.axor.api.Address;
import io.axor.cluster.membership.Member;
import io.axor.cluster.membership.MetaInfo;

import java.util.Set;

/**
 * Represents a member of a cluster with its unique identifier, system name, address, and metadata.
 * <p>
 * This record is immutable and provides methods to query and modify the metadata associated with
 * the cluster member.
 */
public record ClusterMember(long uid, String system, Address address, MetaInfo metaInfo) {
    public static ClusterMember of(Member member) {
        ActorAddress address = member.actor().address();
        return new ClusterMember(member.uid(), address.system(), address.address(),
                member.metaInfo());
    }

    public String selfDatacenter() {
        return metaInfo.get(BuiltinMetaKeys.SELF_DATACENTER);
    }

    public Set<String> selfRoles() {
        return metaInfo.get(BuiltinMetaKeys.SELF_ROLES);
    }

    @Override
    public String toString() {
        return "ClusterMember[" +
               "uid=" + uid +
               ", address=" + system + "@" + address +
               ']';
    }
}
