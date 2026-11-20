package com.blank.imagecloud.models.vo.auth;

import com.blank.imagecloud.models.vo.user.LoginUserVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 登录签发的访问令牌与当前用户资料。 */
@Getter
@RequiredArgsConstructor
@Schema(description = "登录结果")
public class LoginVo {
    @Schema(description = "RS256 签名的 JWT 访问令牌")
    private final String accessToken;

    @Schema(description = "访问令牌类型", example = "Bearer")
    private final String tokenType;

    @Schema(description = "访问令牌有效期，单位为秒", example = "1800")
    private final long expiresIn;

    @Schema(description = "当前登录用户的基础资料")
    private final LoginUserVo user;
}
