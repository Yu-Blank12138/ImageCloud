package com.blank.imagecloud;

import com.blank.imagecloud.config.JwtProperties;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.event.AccessTokenRevokedEvent;
import com.blank.imagecloud.models.vo.entity.UserAccount;
import com.blank.imagecloud.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实 RSA 签名验证会话接口，模拟数据库查询和 Redis 撤销操作。 */
@SpringBootTest(properties = {"spring.sql.init.mode=never", "imagecloud.auth.jwt.access-token-ttl=5m"})
@AutoConfigureMockMvc
@RecordApplicationEvents
class AuthSessionTests {
    private static final String REVOKED_KEY_PREFIX = "imagecloud:auth:revoked:";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private UserAccountMapper userAccountMapper;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ValueOperations<String, String> redisValues;

    /**
     * 确认可用令牌返回数据库中的最新用户资料和 UTC 到期时间。
     */
    @Test
    void returnsCurrentAccountAndTokenExpiry() throws Exception {
        Jwt token = token();
        UserAccount user = new UserAccount();
        user.setId(42L);
        user.setAccount("Alice_01");
        user.setDisplayName("Updated name");
        user.setStatus(UserConstant.STATUS_ACTIVE);
        user.setPlatformRole(UserConstant.ROLE_USER);
        user.setUpdateTime(LocalDateTime.of(2026, 11, 17, 2, 30));
        when(redisTemplate.hasKey(REVOKED_KEY_PREFIX + token.getId())).thenReturn(false);
        when(userAccountMapper.selectById(42L)).thenReturn(user);

        session(token)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.user.id").value(42))
                .andExpect(jsonPath("$.data.user.displayName").value("Updated name"))
                .andExpect(jsonPath("$.data.user.updateTime").value("2026-11-17T02:30:00Z"))
                .andExpect(jsonPath("$.data.expiresAt").value(token.getExpiresAt().toString()))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    /**
     * 确认缺少、格式错误、篡改、过期和声明不符的令牌在访问 Redis 前被拒绝。
     */
    @Test
    void rejectsMissingOrInvalidTokensBeforeAccessingStorage() throws Exception {
        mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4010));
        mvc.perform(post("/api/auth/logout")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4010));
        mvc.perform(get("/api/auth/session").header(HttpHeaders.AUTHORIZATION, "Basic invalid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));

        var parts = token().getTokenValue().split("\\.");
        String badSignature = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/auth/session").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + parts[0] + "." + parts[1] + "." + badSignature))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));

        Jwt expired = JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, 42L, Instant.now().minus(Duration.ofHours(1)));
        session(expired).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));
        logout(expired).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));

        JwtProperties wrongAudience = new JwtProperties();
        wrongAudience.setIssuer(jwtProperties.getIssuer());
        wrongAudience.setAudience("another-api");
        wrongAudience.setAccessTokenTtl(jwtProperties.getAccessTokenTtl());
        Jwt wrongToken = JwtUtil.issueAccessToken(jwtEncoder, wrongAudience, 42L, Instant.now());
        session(wrongToken).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));

        wrongAudience.setIssuer("https://another-issuer.example");
        wrongAudience.setAudience(jwtProperties.getAudience());
        Jwt wrongIssuer = JwtUtil.issueAccessToken(jwtEncoder, wrongAudience, 42L, Instant.now());
        session(wrongIssuer).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));

        Jwt complete = token();
        JwsHeader jwtHeader = JwsHeader.with(SignatureAlgorithm.RS256).build();
        for (String requiredClaim : List.of("aud", "exp", "jti", "sub")) {
            JwtClaimsSet incompleteClaims = JwtClaimsSet.builder().claims(values -> {
                values.putAll(complete.getClaims());
                values.remove(requiredClaim);
            }).build();
            Jwt incomplete = jwtEncoder.encode(JwtEncoderParameters.from(jwtHeader, incompleteClaims));
            session(incomplete).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));
        }
        verifyNoInteractions(redisTemplate, userAccountMapper);
    }

    /**
     * 确认撤销、软删或停用会使当前登录态失效。
     */
    @Test
    void rejectsRevokedTokensAndUnavailableAccounts() throws Exception {
        Jwt token = token();
        String key = REVOKED_KEY_PREFIX + token.getId();
        when(redisTemplate.hasKey(key)).thenReturn(true, false, false);
        session(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));
        verifyNoInteractions(userAccountMapper);

        UserAccount disabled = new UserAccount();
        disabled.setStatus(UserConstant.STATUS_DISABLED);
        // 标准 Mapper 查询自动过滤软删用户，因此此时返回 null。
        when(userAccountMapper.selectById(42L)).thenReturn(null, disabled);
        session(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));
        session(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));
    }

    /**
     * 确认首次注销成功，顺序重试和并发重复撤销均被拒绝，且只发布一次通知。
     *
     * @throws Exception MVC 调用失败时抛出
     */
    @Test
    void revokesOnlyOnceAndRejectsRetries() throws Exception {
        Jwt token = token();
        String key = REVOKED_KEY_PREFIX + token.getId();
        when(redisTemplate.hasKey(key)).thenReturn(false, true, false);
        when(redisTemplate.opsForValue()).thenReturn(redisValues);
        when(redisValues.setIfAbsent(eq(key), eq("1"), any(Duration.class))).thenReturn(true, false);

        logout(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
        logout(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));
        // 模拟并发请求通过查询后，原子写入发现令牌已被其他请求撤销。
        logout(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4012));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(redisValues, times(2)).setIfAbsent(eq(key), eq("1"), ttl.capture());
        for (Duration duration : ttl.getAllValues()) {
            assertThat(duration.toMillis()).isBetween(1L, Duration.ofMinutes(5).toMillis() + 1);
        }
        assertThat(applicationEvents.stream(AccessTokenRevokedEvent.class).map(AccessTokenRevokedEvent::getTokenId))
                .containsExactly(token.getId());
        verifyNoInteractions(userAccountMapper);
    }

    /**
     * 确认 Redis 查询或撤销写入失败时返回服务错误，且注销事件尚未发布。
     *
     * @throws Exception MVC 调用失败时抛出
     */
    @Test
    void failsClosedWhenRedisIsUnavailable() throws Exception {
        Jwt token = token();
        String key = REVOKED_KEY_PREFIX + token.getId();
        when(redisTemplate.hasKey(key)).thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        when(redisTemplate.opsForValue()).thenReturn(redisValues);
        when(redisValues.setIfAbsent(eq(key), eq("1"), any(Duration.class)))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        session(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(5030));
        logout(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(5030));
        verifyNoInteractions(userAccountMapper);

        doReturn(false).when(redisTemplate).hasKey(key);
        logout(token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(5030));
        assertThat(applicationEvents.stream(AccessTokenRevokedEvent.class)).isEmpty();
        verifyNoInteractions(userAccountMapper);
    }

    /**
     * 确认两个接口及其 Bearer 认证声明出现在 Knife4j 使用的 OpenAPI 文档中。
     */
    @Test
    void publishesBearerProtectedOperationsInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.Authorization.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.Authorization.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/session'].get.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].Authorization").exists())
                .andExpect(jsonPath("$.components.schemas.LoginSessionVo.properties.expiresAt.format").value("date-time"));
        mvc.perform(get("/doc.html")).andExpect(status().isOk());
    }

    /**
     * 为测试用户签发访问令牌。
     *
     * @return 含有真实签名的、有效期内的令牌
     */
    private Jwt token() {
        return JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, 42L, Instant.now());
    }

    /**
     * 模拟客户端使用令牌查询当前登录态。
     *
     * @param token 发送给接口的访问令牌
     * @return 查询请求的响应断言入口
     */
    private ResultActions session(Jwt token) throws Exception {
        return mvc.perform(get("/api/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue()));
    }

    /**
     * 模拟客户端注销当前访问令牌。
     *
     * @param token 待撤销的访问令牌
     * @return 注销请求的响应断言入口
     */
    private ResultActions logout(Jwt token) throws Exception {
        return mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue()));
    }
}
