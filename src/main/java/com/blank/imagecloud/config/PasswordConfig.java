package com.blank.imagecloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

import java.util.Map;

/** 配置注册与登录共用的密码哈希格式。 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 版本标识随哈希保存，调整参数后仍可按原版本校验旧密码。
        String encoderId = "pbkdf2-sha256-v1";
        PasswordEncoder pbkdf2 = new Pbkdf2PasswordEncoder(
                "", 16, 600_000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        return new DelegatingPasswordEncoder(encoderId, Map.of(encoderId, pbkdf2));
    }
}
