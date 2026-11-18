package com.blank.imagecloud;

import com.blank.imagecloud.models.vo.entity.UserAccount;
import com.blank.imagecloud.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通过 MVC 验证注册契约，并用 Mapper 替身模拟数据库插入结果。 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
class RegistrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserAccountMapper userAccountMapper;

    /**
     * 验证公开注册的默认权限、密码哈希及应用服务器生成的初始时间。
     */
    @Test
    void registersOnlyAnActiveOrdinaryAccount() throws Exception {
        when(userAccountMapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(42L);
            return 1;
        });

        // 请求中额外提交角色和状态，确认客户端无法覆盖注册默认值。
        register("""
                {"account":" Alice_01 ","password":" 123456 ","displayName":" Alice ",
                 "platformRole":"ADMIN","status":"DISABLED","isDelete":1,
                 "createdAt":"2000-01-01T00:00:00Z","updateTime":"2000-01-01T00:00:00Z",
                 "editTime":"2000-01-01T00:00:00Z"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.account").value("Alice_01"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(saved.capture());
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Alice");
        assertThat(saved.getValue().getPlatformRole()).isEqualTo("USER");
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getValue().getIsDelete()).isZero();
        assertThat(saved.getValue().getPasswordHash()).startsWith("{pbkdf2-sha256-v1}");
        assertThat(passwordEncoder.matches(" 123456 ", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().getCreatedAt()).isNotNull().isAfter(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
        assertThat(saved.getValue().getUpdateTime()).isEqualTo(saved.getValue().getCreatedAt());
        assertThat(saved.getValue().getCreatedAt().getNano() % 1_000_000).isZero();
        assertThat(saved.getValue().getEditTime()).isEqualTo(saved.getValue().getCreatedAt());
    }

    @Test
    void rejectsInvalidAccountOrPasswordBeforeWriting() throws Exception {
        register("""
                {"account":" ab ","password":"password"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4001));
        register("""
                {"account":"alice_01","password":"1234567"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4001));
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void forwardsCaseVariantsAsDistinctAccounts() throws Exception {
        when(userAccountMapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(account.getAccount().equals("Alice_01") ? 42L : 43L);
            return 1;
        });

        // 测试 MVC 和服务层将两种拼写原样传给 Mapper。
        register("""
                {"account":"Alice_01","password":"password"}
                """)
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.account").value("Alice_01"))
                .andExpect(jsonPath("$.data.id").value(42));
        register("""
                {"account":"alice_01","password":"password"}
                """)
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.account").value("alice_01"))
                .andExpect(jsonPath("$.data.id").value(43));
    }

    @Test
    void rejectsNumericPasswordsAsRequestFormatErrors() throws Exception {
        register("""
                {"account":"alice_01","password":12345678}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4000));
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void reportsAnAccountUniqueIndexConflict() throws Exception {
        when(userAccountMapper.insert(any(UserAccount.class))).thenThrow(new DuplicateKeyException("duplicate account"));

        register("""
                {"account":"Alice_01","password":"password"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4090))
                .andExpect(jsonPath("$.message").value("登录账号已被占用"));
    }

    @Test
    void documentsRegistrationInOpenApiAndServesKnife4j() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.RegisterVo.properties.account").exists());
        mvc.perform(get("/v3/api-docs/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists());
        mvc.perform(get("/doc.html")).andExpect(status().isOk());
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
