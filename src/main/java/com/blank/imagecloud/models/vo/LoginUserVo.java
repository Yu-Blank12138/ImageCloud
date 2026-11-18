package com.blank.imagecloud.models.vo;

import com.blank.imagecloud.constant.UserConstant;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.time.Instant;

/** 登录成功后返回给当前用户的基础账号资料。 */
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "当前登录用户的基础资料")
public class LoginUserVo {
    @Schema(description = "用户 ID", example = "42")
    Long id;

    @Schema(description = "保留原始大小写的登录账号", example = "Alice_01")
    String account;

    @Schema(description = "展示昵称", example = "Alice")
    String displayName;

    @Schema(description = "平台角色",
            allowableValues = {UserConstant.ROLE_USER, UserConstant.ROLE_VIP, UserConstant.ROLE_ADMIN})
    String platformRole;

    @Schema(description = "VIP 权益到期时间，使用 UTC", format = "date-time")
    Instant vipExpiresAt;

    @Schema(description = "当前 VIP 权益是否有效")
    boolean vipActive;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "用户最近一次自行修改账号信息的时间，使用 UTC，初始为创建时间，历史记录可能为 null",
            format = "date-time", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
    Instant editTime;

    @Schema(description = "账号记录最近一次变更的时间，使用 UTC",
            format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
    Instant updateTime;
}
