package com.blank.imagecloud.models.dto.user;

import com.blank.imagecloud.common.StrictStringDeserializer;
import com.blank.imagecloud.constant.UserConstant;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 管理员局部更新用户，目标 ID 必填，其他字段省略或为 null 时保持原值。 */
@Getter
@Setter
@Schema(description = "管理员更新用户请求，省略或为 null 的资料字段保持原值")
public final class AdminUpdateUserRequest {
    @NotNull
    @Positive
    @Schema(description = "目标用户 ID", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Size(max = UserConstant.DISPLAY_NAME_MAX_LENGTH)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "新的展示昵称，省略或为 null 时保持原值", nullable = true)
    private String displayName;

    @Pattern(regexp = UserConstant.ROLE_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "新的平台角色，设为 VIP 时必须同时传入 vipExpiresAt",
            allowableValues = {UserConstant.ROLE_USER, UserConstant.ROLE_VIP, UserConstant.ROLE_ADMIN})
    private String platformRole;

    @Pattern(regexp = UserConstant.STATUS_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "新的账号状态", allowableValues = {UserConstant.STATUS_ACTIVE, UserConstant.STATUS_DISABLED})
    private String status;

    @Schema(description = "新的 VIP 到期日期，格式 YYYY-MM-DD，到期当天仍然有效；省略或为 null 时保持原值",
            type = "string", format = "date", nullable = true, example = "2026-12-01")
    private LocalDate vipExpiresAt;

    /** 用户性别，0 未设置，1 男，2 女。 */
    @Min(0)
    @Max(2)
    @Schema(description = "性别，0 未设置，1 男，2 女", allowableValues = {"0", "1", "2"})
    private Integer gender;

    /** 用户自我介绍，空字符串可清空。 */
    @Size(max = UserConstant.BIO_MAX_LENGTH)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "自我介绍，最多 128 个字符")
    private String bio;

    /** 用户公开联系邮箱，空字符串可清空。 */
    @Pattern(regexp = UserConstant.EMAIL_PATTERN)
    @Size(max = UserConstant.EMAIL_MAX_LENGTH)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "联系邮箱，须为 name@example.com 格式，空字符串可清空", format = "email")
    private String email;

    /** 用户手机号，仅本人和管理员可见。 */
    @Pattern(regexp = UserConstant.PHONE_NUMBER_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "手机号，6–20 位数字，可带国际区号前缀 +，空字符串可清空")
    private String phoneNumber;

    /** 用户出生年月，空字符串可清空。 */
    @Pattern(regexp = UserConstant.BIRTH_MONTH_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "出生年月，YYYY-MM，不能晚于当前年月", example = "2000-05")
    private String birthMonth;

    /**
     * 在校验前去除昵称的首尾空白，null 表示保留原值。
     *
     * @param displayName 新昵称，可为空
     */
    @JsonSetter
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.strip();
    }
}
