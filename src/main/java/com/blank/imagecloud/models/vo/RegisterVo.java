package com.blank.imagecloud.models.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/** 注册完成后返回新账号的公开标识。 */
@Value
@Schema(description = "注册结果")
public class RegisterVo {
    @Schema(description = "新用户 ID", example = "42")
    Long id;

    @Schema(description = "用户注册的登录账号，保留大小写", example = "Alice_01")
    String account;
}
