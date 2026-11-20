package com.blank.imagecloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.config.JwtProperties;
import com.blank.imagecloud.constant.AuthConstant;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.dto.auth.LoginRequest;
import com.blank.imagecloud.models.dto.auth.RegisterRequest;
import com.blank.imagecloud.models.event.AccessTokenRevokedEvent;
import com.blank.imagecloud.models.entity.UserAccount;
import com.blank.imagecloud.service.AuthService;
import com.blank.imagecloud.service.UserService;
import com.blank.imagecloud.utils.JwtUtil;
import com.blank.imagecloud.models.vo.user.LoginUserVo;
import com.blank.imagecloud.models.vo.user.LoginSessionVo;
import com.blank.imagecloud.models.vo.auth.LoginVo;
import com.blank.imagecloud.models.vo.auth.RegisterVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

/** 实现账号注册、密码登录和访问令牌会话管理。 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    private static final String REVOKED_TOKEN_MARKER = "1";

    private final UserAccountMapper userAccountMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final String missingAccountPasswordHash;

    /**
     * 注入认证依赖并生成用于登录失败场景的占位哈希。
     *
     * @param userAccountMapper 用于读写用户账号的 Mapper
     * @param userService 提供账号创建操作的用户服务
     * @param passwordEncoder 注册和登录共用的密码编码器
     * @param jwtEncoder 用于签发访问令牌的编码器
     * @param jwtProperties 访问令牌签发参数
     * @param redisTemplate 用于读写令牌撤销记录的 Redis 客户端
     * @param eventPublisher 用于通知协作模块的应用事件发布器
     */
    public AuthServiceImpl(UserAccountMapper userAccountMapper, UserService userService, PasswordEncoder passwordEncoder,
                           JwtEncoder jwtEncoder, JwtProperties jwtProperties,
                           StringRedisTemplate redisTemplate, ApplicationEventPublisher eventPublisher) {
        this.userAccountMapper = userAccountMapper;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
        // 缺失账号也执行一次密码校验，减少失败响应耗时差异。
        this.missingAccountPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 创建正常状态的普通账号，初始权限采用公开注册的默认值。
     *
     * @param request 已校验的注册资料，包含账号、密码和可选昵称
     * @return 新用户 ID 和保留大小写的登录账号
     */
    @Override
    public RegisterVo register(RegisterRequest request) {
        return userService.register(request);
    }

    /**
     * 校验登录凭据和账号状态，签发令牌并返回账号资料。
     *
     * @param request 已校验格式的登录账号和原始密码
     * @return 访问令牌、有效期以及包含账号变更时间的用户资料
     */
    @Override
    public LoginVo login(LoginRequest request) {
        // 账号列区分大小写，MyBatis-Plus 查询自动过滤逻辑删除记录。
        UserAccount account = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getAccount, request.getAccount()));
        String passwordHash = account == null ? missingAccountPasswordHash : account.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), passwordHash);

        ThrowUtils.throwIf(account == null || !passwordMatches || !UserConstant.STATUS_ACTIVE.equals(account.getStatus()),
                ErrorCode.INVALID_CREDENTIALS);

        Instant now = Instant.now();
        Jwt token = JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, account.getId(), now);
        LoginUserVo user = buildLoginUser(account, LocalDateTime.now());
        // 记录登录结果，凭据和令牌内容不写入日志。
        log.info("User logged in: userId={}, account={}", account.getId(), account.getAccount());
        return new LoginVo(token.getTokenValue(), "Bearer", JwtUtil.expiresInSeconds(token), user);
    }

    /**
     * 为会话查询接口读取操作者的最新账号资料并检查账号状态。
     *
     * @param userId 当前请求已验证的操作者 ID
     * @param expiresAt 当前令牌的到期时间
     * @return 最新账号资料和令牌到期时间
     */
    @Override
    public LoginSessionVo getSession(Long userId, Instant expiresAt) {
        UserAccount account = userService.findAccountForAuthentication(userId);
        ThrowUtils.throwIf(account == null || !UserConstant.STATUS_ACTIVE.equals(account.getStatus()),
                ErrorCode.INVALID_ACCESS_TOKEN);
        return new LoginSessionVo(buildLoginUser(account, LocalDateTime.now()), expiresAt);
    }

    /**
     * 原子撤销当前令牌，已撤销时拒绝调用，首次撤销后通知协作模块。
     *
     * @param token 认证拦截器已验签的待撤销令牌
     */
    @Override
    public void logout(Jwt token) {
        Duration remaining = Duration.between(Instant.now(), token.getExpiresAt());

        ThrowUtils.throwIf(remaining.isZero() || remaining.isNegative(), ErrorCode.INVALID_ACCESS_TOKEN);

        // 向上保留到毫秒，保证标记不会先于令牌有效期结束。
        Duration ttl = Duration.ofMillis(remaining.toMillis() + 1);
        boolean revoked = requireRedisResult(() -> redisTemplate.opsForValue()
                .setIfAbsent(AuthConstant.REVOKED_TOKEN_KEY_PREFIX + token.getId(), REVOKED_TOKEN_MARKER, ttl));
        ThrowUtils.throwIf(!revoked, ErrorCode.INVALID_ACCESS_TOKEN);
        log.info("User logged out: userId={}", token.getSubject());
        eventPublisher.publishEvent(new AccessTokenRevokedEvent(token.getId()));
    }

    /**
     * 执行撤销记录的 Redis 操作并校验结果可用性。
     *
     * @param operation 返回查询或写入结果的 Redis 操作
     * @return Redis 确认的布尔结果
     */
    private static boolean requireRedisResult(Supplier<Boolean> operation) {
        Boolean result = null;
        try {
            result = operation.get();
        } catch (DataAccessException ignored) {
            // 无法确认 Redis 状态时返回服务暂不可用。
        }
        ThrowUtils.throwIf(result == null, ErrorCode.SERVICE_UNAVAILABLE);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 将用户记录转换为登录和会话接口返回的公开账号资料。
     *
     * @param account 当前有效账号记录
     * @param now 用于判断 VIP 权益是否到期的当前时间
     * @return 账号资料、当前 VIP 状态与数据库时间字段
     */
    private static LoginUserVo buildLoginUser(UserAccount account, LocalDateTime now) {
        LocalDate vipExpiresAt = account.getVipExpiresAt();
        // 到期日期当天仍然有效，次日起视为已过期。
        boolean vipActive = UserConstant.ROLE_VIP.equals(account.getPlatformRole())
                && vipExpiresAt != null && !vipExpiresAt.isBefore(now.toLocalDate());
        return new LoginUserVo(account.getId(), account.getAccount(), account.getDisplayName(),
                account.getPlatformRole(), vipExpiresAt, vipActive,
                account.getEditTime(), account.getUpdateTime(),
                account.getGender(), account.getBio(), account.getEmail(), account.getPhoneNumber(), account.getBirthMonth());
    }

}
