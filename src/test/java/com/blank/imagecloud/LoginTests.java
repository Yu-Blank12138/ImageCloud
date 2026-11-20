package com.blank.imagecloud;

import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.entity.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通过 MVC 校验登录契约，使用公钥验证响应中的真实 JWT。 */
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "imagecloud.auth.jwt.issuer=https://issuer.example.test",
        "imagecloud.auth.jwt.audience=test-api",
        "imagecloud.auth.jwt.access-token-ttl=7m"
})
@AutoConfigureMockMvc
class LoginTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RSAKey jwtSigningKey;

    @MockitoBean
    private UserAccountMapper userAccountMapper;

    /**
     * 登录成功时验证 JWT 签名，以及用户时间字段的 UTC 序列化。
     */
    @Test
    void logsInAndSignsAnRs256Token() throws Exception {
        UserAccount account = activeAccount();
        account.setVipExpiresAt(LocalDate.now(ZoneOffset.UTC).plusDays(1));
        account.setEditTime(LocalDateTime.of(2026, 11, 17, 1, 15, 0, 123_000_000));
        when(userAccountMapper.selectOne(any())).thenReturn(account);

        var result = login("""
                {"account":" Alice_01 ","password":" 123456 "}
                """)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(420))
                .andExpect(jsonPath("$.data.user.account").value("Alice_01"))
                .andExpect(jsonPath("$.data.user.platformRole").value("VIP"))
                .andExpect(jsonPath("$.data.user.vipActive").value(true))
                .andExpect(jsonPath("$.data.user.editTime").value("2026-11-17T01:15:00.123Z"))
                .andExpect(jsonPath("$.data.user.updateTime").value("2026-11-17T02:30:00Z"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .at("/data/accessToken").asText();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://issuer.example.test"));
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getAudience()).containsExactly("test-api");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(7));
    }

    @Test
    void rejectsInvalidRequestsBeforeQueryingTheAccount() throws Exception {
        login("""
                {"account":"  ","password":"password"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4001));
        login("""
                {"account":"Alice_01","password":12345678}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4000));
        login("""
                {"account":"Alice_01","password":"1234567"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4001));
        login("""
                {"account":"Alice_01","password":"123456789012345678901234567890123"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4001));
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void rejectsWrongPasswordOrUnmatchedAccountCase() throws Exception {
        // 区分大小写的数据库查询对错拼的账号返回空结果。
        when(userAccountMapper.selectOne(any())).thenReturn(activeAccount(), (UserAccount) null);
        assertCredentialsError(login("""
                {"account":"Alice_01","password":"12345678"}
                """));
        assertCredentialsError(login("""
                {"account":"alice_01","password":" 123456 "}
                """));
    }

    /**
     * 验证缺失和停用账号使用同一登录失败响应。
     */
    @Test
    void returnsTheSameErrorForMissingAndDisabledAccounts() throws Exception {
        UserAccount disabled = activeAccount();
        disabled.setStatus(UserConstant.STATUS_DISABLED);

        when(userAccountMapper.selectOne(any())).thenReturn(null, disabled);
        String body = """
                {"account":"Alice_01","password":" 123456 "}
                """;
        assertCredentialsError(login(body));
        assertCredentialsError(login(body));
    }

    /**
     * 验证 VIP 到期后仍可登录，并兼容历史账号编辑时间为空的情况。
     */
    @Test
    void logsInWithAnExpiredVipMembership() throws Exception {
        UserAccount account = activeAccount();
        account.setVipExpiresAt(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        when(userAccountMapper.selectOne(any())).thenReturn(account);

        login("""
                {"account":"Alice_01","password":" 123456 "}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.user.platformRole").value("VIP"))
                .andExpect(jsonPath("$.data.user.vipActive").value(false))
                .andExpect(jsonPath("$.data.user.editTime").value(nullValue()));
    }

    /**
     * 验证登录参数和用户时间字段出现在 OpenAPI 文档中，且 Knife4j 可访问。
     */
    @Test
    void documentsLoginInOpenApiAndServesKnife4j() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.maxLength").value(32))
                .andExpect(jsonPath("$.components.schemas.LoginVo.properties.accessToken").exists())
                .andExpect(jsonPath("$.components.schemas.LoginUserVo.properties.editTime.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.LoginUserVo.properties.editTime.nullable").value(true))
                .andExpect(jsonPath("$.components.schemas.LoginUserVo.properties.updateTime.format").value("date-time"));
        mvc.perform(get("/v3/api-docs/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists());
        mvc.perform(get("/doc.html")).andExpect(status().isOk());
    }

    /**
     * 构造测试中可正常登录的 VIP 账号。
     *
     * @return 带有已编码密码和更新时间的正常账号
     */
    private UserAccount activeAccount() {
        UserAccount account = new UserAccount();
        account.setId(42L);
        account.setAccount("Alice_01");
        account.setDisplayName("Alice");
        account.setPasswordHash(passwordEncoder.encode(" 123456 "));
        account.setPlatformRole(UserConstant.ROLE_VIP);
        account.setStatus(UserConstant.STATUS_ACTIVE);
        account.setIsDelete(UserConstant.NOT_DELETED);
        account.setUpdateTime(LocalDateTime.of(2026, 11, 17, 2, 30));
        return account;
    }

    private ResultActions login(String body) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertCredentialsError(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4011))
                .andExpect(jsonPath("$.message").value("登录凭据错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
