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
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 管理员局部更新用户，目标 ID 必填，其他字段省略或为 null 时保持原值。 */
@Getter
@Setter
@Schema(description = "管理员更新用户请求，省略或为 null 的资料字段保持原值")
public final class AdminUpdateUserRequest {
    @NotNull
    @Positive
    @Schema(description = "目标用户 ID", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Pattern(regexp = UserConstant.ACCOUNT_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "新的登录账号，3–16 位，区分大小写", example = "Alice_02")
    private String account;

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

    @Schema(description = "新的 VIP 到期时间，省略或为 null 时保持原值", format = "date-time", nullable = true)
    private Instant vipExpiresAt;

    /**
     * 在校验前去除账号的首尾空白。
     *
     * @param account 本次提交的登录账号，可为 null
     */
    @JsonSetter
    public void setAccount(String account) {
        this.account = account == null ? null : account.strip();
    }

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
