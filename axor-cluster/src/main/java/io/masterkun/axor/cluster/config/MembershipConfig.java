package io.masterkun.axor.cluster.config;

import com.typesafe.config.Config;
import io.masterkun.axor.commons.config.ConfigField;
import io.masterkun.axor.commons.config.ConfigOrigin;

import java.util.List;

public record MembershipConfig(
        @ConfigField(value = "datacenter", fallback = "default") String datacenter,
        @ConfigField(value = "roles", fallback = "[]", typeArges = String.class) List<String> roles,
        @ConfigField(value = "join", fallback = "{}") JoinConfig join,
        @ConfigField(value = "leave", fallback = "{}") LeaveConfig leave,
        @ConfigField(value = "memberManage", fallback = "{}") MemberManageConfig memberManage,
        @ConfigField(value = "failureDetect", fallback = "{}") FailureDetectConfig failureDetect,
        @ConfigOrigin Config extraConfig) {
}
