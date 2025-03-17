package io.masterkun.axor.cluster.config;

import io.masterkun.axor.commons.config.ConfigField;

public record MembershipConfig(@ConfigField("join") JoinConfig join,
                               @ConfigField("leave") LeaveConfig leave,
                               @ConfigField("memberManage") MemberManageConfig memberManage,
                               @ConfigField("failureDetect") FailureDetectConfig failureDetect) {
}
