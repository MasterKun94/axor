package io.masterkun.axor.cluster.config;

import io.masterkun.axor.commons.config.ConfigField;

public record MembershipConfig(@ConfigField(value = "join", fallback = "{}") JoinConfig join,
                               @ConfigField(value = "leave", fallback = "{}") LeaveConfig leave,
                               @ConfigField(value = "memberManage", fallback = "{}") MemberManageConfig memberManage,
                               @ConfigField(value = "failureDetect", fallback = "{}") FailureDetectConfig failureDetect) {
}
