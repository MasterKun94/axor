package io.masterkun.kactor.cluster.config;

import io.masterkun.kactor.commons.config.ConfigField;

public record MembershipConfig(@ConfigField("join") JoinConfig join,
                               @ConfigField("leave") LeaveConfig leave,
                               @ConfigField("memberManage") MemberManageConfig memberManage,
                               @ConfigField("failureDetect") FailureDetectConfig failureDetect) {
}
