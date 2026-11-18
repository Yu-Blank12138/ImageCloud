package com.blank.imagecloud.service;

import com.blank.imagecloud.models.dto.auth.LoginRequest;
import com.blank.imagecloud.models.dto.auth.RegisterRequest;
import com.blank.imagecloud.models.vo.LoginVo;
import com.blank.imagecloud.models.vo.LoginSessionVo;
import com.blank.imagecloud.models.vo.RegisterVo;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

/** 定义账号认证模块向调用方提供的操作。 */
public interface AuthService {

    /**
     * 使用已校验的注册资料创建普通账号。
     *
     * @param request 已校验的注册资料，包含登录账号、密码和可选昵称
     * @return 新用户 ID 和保留大小写的登录账号
     */
    RegisterVo register(RegisterRequest request);

    /**
     * 校验登录凭据，为有效账号签发访问令牌。
     *
     * @param request 已校验格式的登录账号和原始密码
     * @return 访问令牌、令牌有效期和当前用户资料
     */
    LoginVo login(LoginRequest request);

    /**
     * 查询已通过 JWT 认证的操作者最新账号资料。
     *
     * @param userId 当前请求已验证的操作者 ID
     * @param expiresAt 当前令牌的到期时间
     * @return 账号有效时的最新资料及令牌到期时间
     */
    LoginSessionVo getSession(Long userId, Instant expiresAt);

    /**
     * 撤销本次请求携带的访问令牌。
     *
     * @param token 认证拦截器已验签的待撤销令牌
     */
    void logout(Jwt token);
}
