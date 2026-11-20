package com.blank.imagecloud.models.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

/** 对外公开的用户基本资料。 */
@Value
@Schema(description = "公开用户资料")
public class PublicUserVo {
    @Schema(description = "登录账号，用于区分同名用户", example = "user_01")
    String account;

    /** 公开身份。VIP 到期后按普通用户返回，ADMIN 表示平台管理员。 */
    @Schema(description = "公开身份，USER 普通用户、VIP 会员、ADMIN 平台管理员；VIP 到期后返回 USER",
            example = "VIP", allowableValues = {"USER", "VIP", "ADMIN"})
    String platformRole;

    @Schema(description = "用户昵称", nullable = true)
    String displayName;

    @Schema(description = "可长期访问的固定头像地址，未提供头像时为空", nullable = true)
    String avatarUrl;

    /** 性别，0 未设置，1 男，2 女。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "性别，0 未设置，1 男，2 女", nullable = true)
    Integer gender;

    /** 自我介绍。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "自我介绍", nullable = true)
    String bio;

    /** 公开联系邮箱。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "公开联系邮箱", nullable = true)
    String email;

    /** 出生年月，YYYY-MM。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "出生年月，YYYY-MM", nullable = true)
    String birthMonth;
}
