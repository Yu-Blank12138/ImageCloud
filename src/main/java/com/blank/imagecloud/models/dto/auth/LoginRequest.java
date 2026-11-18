package com.blank.imagecloud.models.dto.auth;

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

/** 登录所需的账号和密码。 */
@Getter
@Schema(description = "账号登录请求")
public final class LoginRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_]{3,32}")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "登录账号，3–32 位英文字母、数字或下划线，忽略首尾空白，区分大小写", example = "Alice_01")
    private final String account;

    @NotBlank
    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "登录密码，8–32 个字符，按输入的原始值校验",
            format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private final String password;

    @JsonCreator
    public LoginRequest(@JsonProperty("account") String account, @JsonProperty("password") String password) {
        // 去除登录账号首尾空白，保留账号大小写和密码原文。
        this.account = account == null ? null : account.strip();
        this.password = password;
    }
}
