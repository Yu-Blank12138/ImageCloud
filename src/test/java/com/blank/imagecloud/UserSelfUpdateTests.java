package com.blank.imagecloud;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blank.imagecloud.config.JwtProperties;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.entity.UserAccount;
import com.blank.imagecloud.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 验证本人更新的身份来源、字段白名单及独立密码校验。 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
class UserSelfUpdateTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private UserAccountMapper mapper;
    @MockitoBean private StringRedisTemplate redis;

    /**
     * 验证压缩头像与原图地址作为用户表字段一起保存。
     */
    @Test
    void savesBothAvatarUrls() throws Exception {
        String auth = signIn("original8");
        when(mapper.update(any(UserAccount.class), any())).thenReturn(1);
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"avatarUrl":"https://example.com/avatar.webp",
                 "avatarOriginalUrl":"https://example.com/avatar.png"}
                """)).andExpect(content().json("{\"code\":200}", true));
        var changes = ArgumentCaptor.forClass(UserAccount.class);
        verify(mapper).update(changes.capture(), any());
        assertThat(changes.getValue().getAvatarUrl()).isEqualTo("https://example.com/avatar.webp");
        assertThat(changes.getValue().getAvatarOriginalUrl()).isEqualTo("https://example.com/avatar.png");
    }

    /**
     * 验证资料更新忽略 null，并使用登录用户 ID 和字段白名单。
     */
    @Test
    void updatesOnlyOwnSubmittedProfileFields() throws Exception {
        String auth = signIn("original8");
        when(mapper.update(any(UserAccount.class), any())).thenReturn(1);
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"id":999,"account":null,"displayName":" New name ","avatarUrl":null,
                 "platformRole":"ADMIN","passwordHash":"injected","editTime":"2000-01-01T00:00:00Z"}
                """)).andExpect(content().json("{\"code\":200}", true));
        var changes = ArgumentCaptor.forClass(UserAccount.class);
        verify(mapper).update(changes.capture(), any());
        assertThat(changes.getValue().getId()).isEqualTo(42L);
        assertThat(changes.getValue().getDisplayName()).isEqualTo("New name");
        assertThat(changes.getValue().getAccount()).isNull();
        assertThat(changes.getValue().getAvatarUrl()).isNull();
        assertThat(changes.getValue().getPlatformRole()).isNull();
        assertThat(changes.getValue().getPasswordHash()).isNull();
        assertThat(changes.getValue().getEditTime()).isNotNull().isEqualTo(changes.getValue().getUpdateTime());
    }

    /**
     * 验证全空更新被拒绝，空字符串可显式清空昵称和头像。
     */
    @Test
    void rejectsEmptyProfileAndAllowsExplicitClearing() throws Exception {
        String auth = signIn("original8");
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"account\":null}"))
                .andExpect(jsonPath("$.code").value(4001));
        verify(mapper, never()).update(any(UserAccount.class), any());
        when(mapper.update(any(UserAccount.class), any())).thenReturn(1);
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"\",\"avatarUrl\":\"\"}"))
                .andExpect(jsonPath("$.code").value(200));
        var changes = ArgumentCaptor.forClass(UserAccount.class);
        verify(mapper).update(changes.capture(), any());
        assertThat(changes.getValue().getDisplayName()).isEmpty();
        assertThat(changes.getValue().getAvatarUrl()).isEmpty();
    }

    /**
     * 验证非法账号和头像在写入前被拒绝。
     */
    @Test
    void validatesProfileFields() throws Exception {
        String auth = signIn("original8");
        for (String body : new String[]{"{\"account\":\"a\"}", "{\"avatarUrl\":\"http://example.com/a.png\"}",
                "{\"displayName\":42}"}) {
            mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(body.contains(":42") ? 4000 : 4001));
        }
        verify(mapper, never()).update(any(UserAccount.class), any());
    }

    /**
     * 验证密码更新保留输入空白，原哈希参与更新条件，新密码以哈希形式保存。
     */
    @Test
    void changesPasswordWithOriginalHashCondition() throws Exception {
        String auth = signIn(" original8 ");
        when(mapper.update(any(UserAccount.class), any())).thenReturn(1);
        mvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999,\"oldPassword\":\" original8 \",\"newPassword\":\" newPass88 \"}"))
                .andExpect(content().json("{\"code\":200}", true));
        var changes = ArgumentCaptor.forClass(UserAccount.class);
        var conditions = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).update(changes.capture(), conditions.capture());
        assertThat(passwordEncoder.matches(" newPass88 ", changes.getValue().getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("newPass88", changes.getValue().getPasswordHash())).isFalse();
        assertThat(conditions.getValue().getSqlSegment()).contains("id =", "password_hash =", "status =");
        assertThat(conditions.getValue().getParamNameValuePairs().values()).contains(42L, UserConstant.STATUS_ACTIVE);
        assertThat(changes.getValue().getEditTime()).isEqualTo(changes.getValue().getUpdateTime());
        assertThat(changes.getValue().getAccount()).isNull();
    }

    /**
     * 验证原密码错误和缺少密码字段时不修改账号。
     */
    @Test
    void rejectsWrongOrMissingPasswords() throws Exception {
        String auth = signIn("original8");
        mvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"wrongPass8\",\"newPassword\":\"newPass88\"}"))
                .andExpect(jsonPath("$.code").value(4011));
        mvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"short\"}"))
                .andExpect(jsonPath("$.code").value(4001));
        verify(mapper, never()).update(any(UserAccount.class), any());
    }

    /**
     * 验证并发期间原哈希变化时返回冲突。
     */
    @Test
    void rejectsConcurrentPasswordChange() throws Exception {
        String auth = signIn("original8");
        when(mapper.update(any(UserAccount.class), any())).thenReturn(0);
        mvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"original8\",\"newPassword\":\"newPass88\"}"))
                .andExpect(jsonPath("$.code").value(4090));
    }

    /**
     * 验证两个接口要求登录，并在文档中展示分离的资料和密码请求。
     */
    @Test
    void requiresLoginAndDocumentsSeparateRequests() throws Exception {
        for (String path : new String[]{"/api/users/me", "/api/users/me/password"}) {
            mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(jsonPath("$.code").value(4010));
        }
        mvc.perform(get("/v3/api-docs/default"))
                .andExpect(jsonPath("$.paths['/api/users/me'].patch.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/users/me/password'].patch.security[0].Authorization").exists())
                .andExpect(jsonPath("$.components.schemas.UserUpdateRequest.properties.id").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.components.schemas.UserUpdateRequest.properties.newPassword").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.components.schemas.UserPasswordUpdateRequest.properties.newPassword.writeOnly").value(true));
    }

    /**
     * 模拟正常账号并签发真实 JWT。
     *
     * @param password 当前账号密码
     * @return 可用于测试请求的 Bearer 请求头
     */
    private String signIn(String password) {
        UserAccount account = new UserAccount();
        account.setId(42L);
        account.setAccount("SelfUser");
        account.setPlatformRole(UserConstant.ROLE_USER);
        account.setStatus(UserConstant.STATUS_ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(password));
        when(mapper.selectById(42L)).thenReturn(account);
        var token = JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, 42L, Instant.now());
        when(redis.hasKey("imagecloud:auth:revoked:" + token.getId())).thenReturn(false);
        return "Bearer " + token.getTokenValue();
    }
}
