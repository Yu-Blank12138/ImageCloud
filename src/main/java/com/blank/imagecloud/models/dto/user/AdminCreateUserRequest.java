package com.blank.imagecloud.models.dto.user;

import com.blank.imagecloud.common.StrictStringDeserializer;
import com.blank.imagecloud.constant.UserConstant;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.Instant;

/** 管理员创建账号时可提交的字段。 */
@Getter
@Schema(description = "管理员创建用户请求，初始密码由服务端设置为 12345678")
public final class AdminCreateUserRequest {
    @NotBlank
    @Pattern(regexp = UserConstant.ACCOUNT_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "登录账号，3–16 位英文字母、数字或下划线，区分大小写", example = "Alice_01")
    private final String account;

    @Size(max = UserConstant.DISPLAY_NAME_MAX_LENGTH)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "可选展示昵称", example = "Alice")
    private final String displayName;

    @Pattern(regexp = UserConstant.ROLE_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "平台角色", defaultValue = UserConstant.ROLE_USER,
            allowableValues = {UserConstant.ROLE_USER, UserConstant.ROLE_VIP, UserConstant.ROLE_ADMIN})
    private final String platformRole;

    @Pattern(regexp = UserConstant.STATUS_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "账号状态", defaultValue = UserConstant.STATUS_ACTIVE,
            allowableValues = {UserConstant.STATUS_ACTIVE, UserConstant.STATUS_DISABLED})
    private final String status;

    @Schema(description = "VIP 到期时间，角色为 VIP 时必填", format = "date-time")
    private final Instant vipExpiresAt;

    /**
     * 读取允许提交的账号字段，并在校验前规整账号和昵称的首尾空白。
     *
     * @param account 区分大小写的登录账号
     * @param displayName 可选的展示昵称
     * @param platformRole 可选的平台角色
     * @param status 可选的账号状态
     * @param vipExpiresAt 可选的 VIP 到期时间
     */
    @JsonCreator
    public AdminCreateUserRequest(@JsonProperty("account") String account,
                                  @JsonProperty("displayName") String displayName,
                                  @JsonProperty("platformRole") String platformRole,
                                  @JsonProperty("status") String status,
                                  @JsonProperty("vipExpiresAt") Instant vipExpiresAt) {
        this.account = account == null ? null : account.strip();
        this.displayName = displayName == null ? null : displayName.strip();
        this.platformRole = platformRole;
        this.status = status;
        this.vipExpiresAt = vipExpiresAt;
    }
}
