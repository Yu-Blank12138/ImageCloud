package com.blank.imagecloud.models.vo.user;

import com.blank.imagecloud.constant.UserConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Schema(description = "压缩头像公开固定 URL", nullable = true)
    String avatarUrl;

    /** 用户保存的头像原图地址。 */
    @Schema(description = "头像原图公开固定 URL", nullable = true)
    String avatarOriginalUrl;

    @Schema(description = "平台角色",
            allowableValues = {UserConstant.ROLE_USER, UserConstant.ROLE_VIP, UserConstant.ROLE_ADMIN})
    String platformRole;

    @Schema(description = "账号状态", allowableValues = {UserConstant.STATUS_ACTIVE, UserConstant.STATUS_DISABLED})
    String status;

    @Schema(description = "VIP 权益到期日期，到期当天仍然有效", type = "string", format = "date", nullable = true)
    LocalDate vipExpiresAt;

    @Schema(description = "逻辑删除标记", example = "0")
    Integer isDelete;

    @Schema(description = "账号创建时间，使用本地日期时间", type = "string", format = "local-date-time")
    LocalDateTime createdAt;

    @Schema(description = "用户最近一次自行修改资料的时间，使用本地日期时间，初始为创建时间，历史记录可能为空",
            type = "string", format = "local-date-time", nullable = true)
    LocalDateTime editTime;

    @Schema(description = "账号记录最近一次更新的时间，使用本地日期时间", type = "string", format = "local-date-time")
    LocalDateTime updateTime;

    /** 性别，0 未设置，1 男，2 女。 */
    @Schema(description = "性别，0 未设置，1 男，2 女", nullable = true)
    Integer gender;

    /** 自我介绍。 */
    @Schema(description = "自我介绍", nullable = true)
    String bio;

    /** 公开联系邮箱。 */
    @Schema(description = "公开联系邮箱", nullable = true)
    String email;

    /** 手机号，仅本人和管理员可见。 */
    @Schema(description = "手机号，仅本人和管理员可见", nullable = true)
    String phoneNumber;

    /** 出生年月，YYYY-MM。 */
    @Schema(description = "出生年月，YYYY-MM", nullable = true)
    String birthMonth;
}
