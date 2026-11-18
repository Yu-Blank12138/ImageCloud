package com.blank.imagecloud.models.vo;

import com.blank.imagecloud.constant.UserConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.time.Instant;

/** 管理员可查看的用户资料。 */
@Value
@Schema(description = "管理员用户详情")
public class AdminUserVo {
    @Schema(description = "用户 ID", example = "42")
    Long id;

    @Schema(description = "原始登录账号", example = "Alice_01")
    String account;

    @Schema(description = "用户昵称", nullable = true)
    String displayName;

    @Schema(description = "已保存的原始 COS 头像地址", nullable = true)
    String avatarUrl;

    @Schema(description = "平台角色",
            allowableValues = {UserConstant.ROLE_USER, UserConstant.ROLE_VIP, UserConstant.ROLE_ADMIN})
    String platformRole;

    @Schema(description = "账号状态", allowableValues = {UserConstant.STATUS_ACTIVE, UserConstant.STATUS_DISABLED})
    String status;

    @Schema(description = "VIP 权益到期时间，使用 UTC", format = "date-time", nullable = true)
    Instant vipExpiresAt;

    @Schema(description = "逻辑删除标记", example = "0")
    Integer isDelete;

    @Schema(description = "账号创建时间，使用 UTC", format = "date-time")
    Instant createdAt;

    @Schema(description = "用户最近一次自行修改资料的时间，使用 UTC，初始为创建时间，历史记录可能为空",
            format = "date-time", nullable = true)
    Instant editTime;

    @Schema(description = "账号记录最近一次更新的时间，使用 UTC", format = "date-time")
    Instant updateTime;
}
