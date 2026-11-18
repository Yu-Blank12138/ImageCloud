package com.blank.imagecloud.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 登录访问令牌的签发参数。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "imagecloud.auth.jwt")
public class JwtProperties {
    /** 令牌签发者。 */
    @NotBlank
    private String issuer;

    /** 接受该令牌的 API 标识。 */
    @NotBlank
    private String audience;

    /** 访问令牌有效期。 */
    @NotNull
    private Duration accessTokenTtl;

    /** RSA 私钥的 PKCS#8 PEM 文件位置。 */
    @NotBlank
    private String privateKeyLocation;
}
