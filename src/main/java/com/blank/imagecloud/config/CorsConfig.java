package com.blank.imagecloud.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 为独立部署的前端配置 REST 接口的浏览器跨域访问。 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 前端通过 Authorization 请求头传递 Bearer 令牌，因此关闭跨域 Cookie 凭据。
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
