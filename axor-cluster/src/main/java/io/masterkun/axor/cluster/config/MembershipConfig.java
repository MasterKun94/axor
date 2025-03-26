package io.masterkun.axor.cluster.config;

import io.masterkun.axor.commons.config.ConfigField;

import java.util.List;

public record MembershipConfig(
        @ConfigField(value = "datacenter", fallback = "default") String datacenter,
        @ConfigField(value = "roles", fallback = "[]", typeArges = String.class) List<String> roles,
        @ConfigField(value = "join", fallback = "{}") JoinConfig join,
        @ConfigField(value = "leave", fallback = "{}") LeaveConfig leave,
        @ConfigField(value = "memberManage", fallback = "{}") MemberManageConfig memberManage,
        @ConfigField(value = "failureDetect", fallback = "{}") FailureDetectConfig failureDetect) {
}
