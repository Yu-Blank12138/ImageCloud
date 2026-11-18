package com.blank.imagecloud.utils;

import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.config.JwtProperties;
import com.blank.imagecloud.exception.ErrorCode;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** 签发和验证访问令牌的静态工具方法。 */
public final class JwtUtil {
    private static final Pattern BEARER_HEADER = Pattern.compile("^Bearer +([^\\s,]+)$", Pattern.CASE_INSENSITIVE);

    private JwtUtil() {
    }

    /**
     * 使用配置的签发者、受众和有效期，为用户签发 RS256 访问令牌。
     *
     * @param encoder 使用 RSA 私钥签名的 JWT 编码器
     * @param properties 包含签发者、受众和有效期的令牌配置
     * @param userId 登录用户的数据库主键，作为令牌主体
     * @param now 本次签发使用的当前时刻
     * @return 已签名的 JWT 对象，包含令牌字符串和各项声明
     */
    public static Jwt issueAccessToken(JwtEncoder encoder, JwtProperties properties, Long userId, Instant now) {
        // JWT 时间声明使用秒级精度。
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        long expiresIn = properties.getAccessTokenTtl().toSeconds();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience()))
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(expiresIn))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims));
    }

    /**
     * 根据令牌的签发和到期时间计算有效期。
     *
     * @param token 包含签发时间和到期时间的 JWT
     * @return 令牌签发时的完整有效期，单位为秒
     */
    public static long expiresInSeconds(Jwt token) {
        return Duration.between(token.getIssuedAt(), token.getExpiresAt()).toSeconds();
    }

    /**
     * 从 Authorization 请求头读取并验证 Bearer 令牌。
     *
     * @param decoder 配有公钥、算法和声明校验规则的 JWT 解码器
     * @param authorization Authorization 请求头，缺失时为空
     * @return 签名与声明均已通过校验的令牌
     */
    public static Jwt verifyBearerToken(JwtDecoder decoder, String authorization) {
        ThrowUtils.throwIf(!StringUtils.hasText(authorization), ErrorCode.AUTHENTICATION_REQUIRED);
        var matcher = BEARER_HEADER.matcher(authorization.strip());
        ThrowUtils.throwIf(!matcher.matches(), ErrorCode.INVALID_ACCESS_TOKEN);

        Jwt token = null;
        try {
            token = decoder.decode(matcher.group(1));
        } catch (JwtException ignored) {
            // 签名、解析和声明校验失败采用同一业务错误码。
        }
        ThrowUtils.throwIf(token == null, ErrorCode.INVALID_ACCESS_TOKEN);
        return token;
    }

    /**
     * 校验访问令牌的标识、用户主体和严格到期边界。
     *
     * @param token 已验签并完成声明类型转换的 JWT
     * @return 令牌 ID、正数用户 ID 和未来到期时间均有效时返回成功
     */
    public static OAuth2TokenValidatorResult validateAccessTokenClaims(Jwt token) {
        OAuth2Error invalid = new OAuth2Error("invalid_token", "Invalid access token claims", null);
        // JWT 到期后立即拒绝，与 Redis 撤销标记的保留期限一致。
        if (!StringUtils.hasText(token.getId()) || token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(Instant.now())) {
            return OAuth2TokenValidatorResult.failure(invalid);
        }
        try {
            if (Long.parseLong(token.getSubject()) > 0) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException ignored) {
            // 用户主体缺失或超出数据库主键的数值范围。
        }
        return OAuth2TokenValidatorResult.failure(invalid);
    }
}
