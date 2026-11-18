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

import java.io.Serializable;

/** 公开注册接收的账号资料。 */
@Getter
@Schema(description = "账号注册请求")
public final class RegisterRequest implements Serializable {



    @NotBlank
    @Pattern(regexp = UserConstant.ACCOUNT_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "登录账号，3–16 位英文字母、数字或下划线，忽略首尾空白，区分大小写", example = "Alice_01")
    private final String account;

    @NotBlank
    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "登录密码，8–32 位", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private final String password;

    @Size(max = UserConstant.DISPLAY_NAME_MAX_LENGTH)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "可选展示昵称，最多 64 位，空白时按未设置处理", example = "Alice")
    private final String displayName;

    @JsonCreator
    public RegisterRequest(@JsonProperty("account") String account,
                           @JsonProperty("password") String password,
                           @JsonProperty("displayName") String displayName) {
        // 账号与昵称在校验前去掉首尾空白，密码保留原始输入。
        this.account = account == null ? null : account.strip();
        this.password = password;
        this.displayName = displayName == null ? null : displayName.strip();
    }

    /**
     * 生成调试用的文本表示，避免泄露注册时提交的密码。
     *
     * @return 仅包含请求类型名称的字符串
     */
    @Override
    public String toString() {
        return "RegisterRequest";
    }

}
