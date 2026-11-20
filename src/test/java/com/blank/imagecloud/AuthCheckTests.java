package com.blank.imagecloud;

import com.blank.imagecloud.annotation.AuthCheck;
import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.config.JwtProperties;
import com.blank.imagecloud.constant.PlatformRole;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.entity.UserAccount;
import com.blank.imagecloud.response.Response;
import com.blank.imagecloud.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通过 MVC 调用受切面保护的方法，使用真实 JWT 和模拟的数据存储验证权限。
 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
@Import(AuthCheckTests.ProbeController.class)
class AuthCheckTests {
    private static final long USER_ID = 42L;
    private static final String REVOKED_KEY_PREFIX = "imagecloud:auth:revoked:";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private UserAccountMapper userAccountMapper;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    /**
     * 验证同一 JWT 在后续请求中采用数据库最新的会员到期时间和账号状态。
     *
     * @throws Exception MVC 调用失败时抛出
     */
    @Test
    void readsLatestVipExpiryAndAccountStatus() throws Exception {
        Jwt token = JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, USER_ID, Instant.now());
        when(redisTemplate.hasKey(REVOKED_KEY_PREFIX + token.getId())).thenReturn(false);
        UserAccount user = new UserAccount();
        user.setId(USER_ID);
        user.setStatus(UserConstant.STATUS_ACTIVE);
        user.setPlatformRole(UserConstant.ROLE_VIP);
        user.setVipExpiresAt(LocalDate.now(ZoneOffset.UTC).plusDays(1));
        when(userAccountMapper.selectById(USER_ID)).thenReturn(user);
        String authorization = "Bearer " + token.getTokenValue();

        mvc.perform(get("/api/test/auth-check/vip").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$.code").value(200));
        user.setVipExpiresAt(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        mvc.perform(get("/api/test/auth-check/vip").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$.code").value(4030));
        user.setStatus(UserConstant.STATUS_DISABLED);
        mvc.perform(get("/api/test/auth-check/user").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$.code").value(4012));
        when(userAccountMapper.selectById(USER_ID)).thenReturn(null);
        mvc.perform(get("/api/test/auth-check/user").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$.code").value(4012));
    }

    /**
     * 验证三种角色的访问矩阵，并覆盖到期会员和未知角色。
     *
     * @param role 数据库中的平台角色
     * @param vipActive 会员到期时间是否仍在将来
     * @param endpoint 声明对应角色要求的测试路径
     * @param expectedCode 响应体应返回的业务码
     * @throws Exception MVC 调用失败时抛出
     */
    @ParameterizedTest
    @CsvSource({
            "USER, false, user, 200",
            "USER, false, vip, 4030",
            "USER, false, admin, 4030",
            "VIP, true, user, 200",
            "VIP, true, vip, 200",
            "VIP, true, admin, 4030",
            "VIP, false, user, 200",
            "VIP, false, vip, 4030",
            "ADMIN, false, user, 200",
            "ADMIN, false, vip, 200",
            "ADMIN, false, admin, 200",
            "UNKNOWN, false, user, 4030"
    })
    void checksMinimumRole(String role, boolean vipActive, String endpoint, int expectedCode) throws Exception {
        Instant now = Instant.now();
        Jwt token = JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, USER_ID, now);
        UserAccount account = new UserAccount();
        account.setId(USER_ID);
        account.setPlatformRole(role);
        account.setStatus(UserConstant.STATUS_ACTIVE);
        if (UserConstant.ROLE_VIP.equals(role)) {
            account.setVipExpiresAt(LocalDate.now(ZoneOffset.UTC).plusDays(vipActive ? 1 : -1));
        }
        when(userAccountMapper.selectById(USER_ID)).thenReturn(account);
        when(redisTemplate.hasKey(REVOKED_KEY_PREFIX + token.getId())).thenReturn(false);

        mvc.perform(get("/api/test/auth-check/" + endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode));
        verify(userAccountMapper).selectById(USER_ID);
        verify(redisTemplate).hasKey(REVOKED_KEY_PREFIX + token.getId());
    }

    /**
     * 提供仅在本测试上下文注册的鉴权入口。
     */
    @TestComponent
    @RestController
    static class ProbeController {
        /**
         * 供测试验证注解的默认权限。
         *
         * @return 通用成功响应
         */
        @AuthCheck
        @GetMapping("/api/test/auth-check/user")
        public Response<Void> user() {
            return ResultUtil.ok();
        }

        /**
         * 供测试验证会员权限。
         *
         * @return 通用成功响应
         */
        @AuthCheck(PlatformRole.VIP)
        @GetMapping("/api/test/auth-check/vip")
        public Response<Void> vip() {
            return ResultUtil.ok();
        }

        /**
         * 供测试验证管理员权限。
         *
         * @return 通用成功响应
         */
        @AuthCheck(PlatformRole.ADMIN)
        @GetMapping("/api/test/auth-check/admin")
        public Response<Void> admin() {
            return ResultUtil.ok();
        }
    }
}
