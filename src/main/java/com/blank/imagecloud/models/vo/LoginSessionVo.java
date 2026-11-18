package com.blank.imagecloud.models.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.time.Instant;

/** 当前令牌对应的有效登录态。 */
@Value
@Schema(description = "当前登录态")
public class LoginSessionVo {
    @Schema(description = "当前账号的最新资料")
    LoginUserVo user;

    @Schema(description = "当前访问令牌的 UTC 到期时间", format = "date-time")
    Instant expiresAt;
}
