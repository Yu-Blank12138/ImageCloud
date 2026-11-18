package com.blank.imagecloud.config;

import com.blank.imagecloud.utils.JwtUtil;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;

/** 从部署环境加载固定的 RSA 签名密钥，用于签发登录 JWT。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public RSAKey jwtSigningKey(JwtProperties properties, ResourceLoader resourceLoader)
            throws IOException, GeneralSecurityException {
        var location = resourceLoader.getResource(properties.getPrivateKeyLocation());
        try (InputStream input = location.getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(input);
            if (!(privateKey instanceof RSAPrivateCrtKey crtKey) || privateKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("JWT signing requires a PKCS#8 RSA private key of at least 2048 bits");
            }

            // 从同一私钥派生公钥，保证签发用的密钥对匹配。
            var publicSpec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
            var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicSpec);
            return new RSAKey.Builder(publicKey).privateKey(privateKey).algorithm(JWSAlgorithm.RS256).build();
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey jwtSigningKey, JwtProperties properties) {
        if (properties.getAccessTokenTtl().toSeconds() < 1) {
            throw new IllegalArgumentException("JWT access-token-ttl must be at least one second");
        }
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /**
     * 配置访问令牌的 RS256 验签和声明校验。
     *
     * @param jwtSigningKey 用于取得验签公钥的 RSA 密钥对
     * @param properties 此服务要求的签发者和受众配置
     * @return 使用本服务公钥并严格按声明到期的 JWT 解码器
     * @throws JOSEException 公钥无法转换时抛出
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(properties.getIssuer()),
                new JwtAudienceValidator(properties.getAudience()),
                new JwtTimestampValidator(Duration.ZERO),
                JwtUtil::validateAccessTokenClaims));
        return decoder;
    }
}
