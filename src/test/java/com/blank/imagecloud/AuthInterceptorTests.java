package com.blank.imagecloud;

import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.config.JwtProperties;
import com.blank.imagecloud.constant.AuthConstant;
import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.response.Response;
import com.blank.imagecloud.utils.JwtUtil;
import org.junit.jupiter.api.Test;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通过 MVC 验证 API 认证边界与当前操作者的线程生命周期。 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
@Import(AuthInterceptorTests.OperatorController.class)
class AuthInterceptorTests {
    private static final long USER_ID = 42L;
    private static final String OPERATOR_PATH = "/api/test/operator";

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
     * 验证无角色注解的接口也拒绝缺失、无效或已撤销的令牌。
     *
     * @throws Exception MVC 调用失败时抛出
     */
    @Test
    void rejectsInvalidSessionsBeforeLoadingAccount() throws Exception {
        Jwt revoked = JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, USER_ID, Instant.now());
        when(redisTemplate.hasKey(AuthConstant.REVOKED_TOKEN_KEY_PREFIX + revoked.getId())).thenReturn(true);

        mvc.perform(get("/api/heartbeat")).andExpect(jsonPath("$.code").value(4010));
        mvc.perform(get(OPERATOR_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(jsonPath("$.code").value(4012));
        mvc.perform(get(OPERATOR_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + revoked.getTokenValue()))
                .andExpect(jsonPath("$.code").value(4012));
        verifyNoInteractions(userAccountMapper);
        assertThat(CurrentUserContext.getUserId()).isNull();
    }

    /**
     * 验证无需查询账号即可取得操作者 ID，并在成功或异常结束后清理线程。
     *
     * @throws Exception MVC 调用失败时抛出
     */
    @Test
    void bindsAndClearsOperatorOnTheRequestThread() throws Exception {
        Jwt token = JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, USER_ID, Instant.now());
        when(redisTemplate.hasKey(AuthConstant.REVOKED_TOKEN_KEY_PREFIX + token.getId())).thenReturn(false);
        String authorization = "Bearer " + token.getTokenValue();

        mvc.perform(get(OPERATOR_PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value((int) USER_ID));
        assertThat(CurrentUserContext.getUserId()).isNull();

        mvc.perform(get(OPERATOR_PATH).param("fail", "true").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$.code").value(4090));
        assertThat(CurrentUserContext.getUserId()).isNull();

        mvc.perform(get(OPERATOR_PATH)).andExpect(jsonPath("$.code").value(4010));
        mvc.perform(get("/api/heartbeat").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verifyNoInteractions(userAccountMapper);
    }

    /**
     * 验证浏览器可以预检携带 Authorization 的后续请求。
     *
     * @throws Exception MVC 调用失败时抛出
     */
    @Test
    void allowsCorsPreflightWithoutToken() throws Exception {
        mvc.perform(options(OPERATOR_PATH)
                        .header(HttpHeaders.ORIGIN, "https://client.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
        verifyNoInteractions(userAccountMapper, redisTemplate);
    }

    /**
     * 验证接口文档公开可访问，且心跳接口声明 Bearer 认证。
     *
     * @throws Exception MVC 调用失败时抛出
     */
    @Test
    void documentsHeartbeatAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs/default")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/heartbeat'].get.security[0].Authorization").exists());
        mvc.perform(get("/doc.html")).andExpect(status().isOk());
    }

    /** 仅在测试环境暴露读取线程上下文的入口。 */
    @TestComponent
    @RestController
    static class OperatorController {
        /**
         * 返回当前线程上的操作者，或模拟业务异常以验证清理。
         *
         * @param fail 是否在取得操作者后抛出业务异常
         * @return 包含已验证操作者 ID 的成功响应
         */
        @GetMapping(OPERATOR_PATH)
        public Response<Long> operator(@RequestParam(name = "fail", defaultValue = "false") boolean fail) {
            Long userId = CurrentUserContext.requireUserId();
            ThrowUtils.throwIf(fail, ErrorCode.CONFLICT);
            return ResultUtil.ok(userId);
        }
    }
}
