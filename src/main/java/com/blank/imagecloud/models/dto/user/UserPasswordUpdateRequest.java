package com.blank.imagecloud.models.dto.user;

import com.blank.imagecloud.common.StrictStringDeserializer;
import com.blank.imagecloud.constant.UserConstant;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 用户本人修改密码，密码按原始输入进行校验。 */
@Getter
@Setter
@Schema(description = "用户本人修改密码请求")
public class UserPasswordUpdateRequest {
    /** 用于校验身份的当前密码。 */
    @NotBlank
    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "原密码，8–32 个字符", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String oldPassword;

    /** 校验原密码后进行哈希保存的新密码。 */
    @NotBlank
    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "新密码，8–32 个字符", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String newPassword;
}
