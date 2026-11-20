package com.blank.imagecloud;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blank.imagecloud.config.JwtProperties;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.entity.UserAccount;
import com.blank.imagecloud.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证用户详情接口的权限、字段范围和接口文档。 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
class UserDetailsTests {
    private static final long ACTOR_ID = 7L;
    private static final String ADMIN_PATH = "/api/admin/users/{id}";
    private static final String PUBLIC_PATH = "/api/users/{id}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserAccountMapper userAccountMapper;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    /**
     * 验证管理员可读取停用账号的完整资料，返回结果排除密码。
     */
    @Test
    void adminGetsUnmaskedDetailsOfDisabledAccount() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        UserAccount target = targetUser();
        target.setStatus(UserConstant.STATUS_DISABLED);
        when(userAccountMapper.selectOne(any())).thenReturn(target);

        mvc.perform(get(ADMIN_PATH, 42).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.account").value("Alice_01"))
                .andExpect(jsonPath("$.data.avatarUrl").value(target.getAvatarUrl()))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.isDelete").value(0))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-17T02:30:00Z"))
                .andExpect(jsonPath("$.data.updateTime").value("2026-11-17T03:30:00Z"))
                .andExpect(jsonPath("$.data.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.passwordHash").doesNotHaveJsonPath());
    }

    /**
     * 验证匿名访问者和普通用户均无法查询管理详情。
     */
    @Test
    void restrictsAdminDetailsToAdmins() throws Exception {
        mvc.perform(get(ADMIN_PATH, 42)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4010));
        verifyNoInteractions(userAccountMapper);

        String user = bearerForRole(UserConstant.ROLE_USER);
        mvc.perform(get(ADMIN_PATH, 42).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(4030));
        verify(userAccountMapper, never()).selectOne(any());
    }

    /**
     * 验证登录用户读取公开字段和已保存的固定头像地址。
     */
    @Test
    void returnsOnlyPublicFieldsForSignedInUser() throws Exception {
        mvc.perform(get(PUBLIC_PATH, 42)).andExpect(jsonPath("$.code").value(4010));
        verifyNoInteractions(userAccountMapper, redisTemplate);

        when(userAccountMapper.selectOne(any())).thenReturn(targetUser());
        String user = bearerForRole(UserConstant.ROLE_USER);
        var result = mvc.perform(get(PUBLIC_PATH, 42).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsByteArray()).get("data"))
                .isEqualTo(json.readTree("""
                        {"id":42,"displayName":"Alice",
                         "avatarUrl":"https://example.cos.ap-beijing.myqcloud.com/avatars/alice.png"}
                        """));
    }

    /**
     * 验证非法 ID 和未找到的用户采用现有业务错误码。
     */
    @Test
    void rejectsInvalidOrMissingUserIds() throws Exception {
        String user = bearerForRole(UserConstant.ROLE_USER);
        mvc.perform(get(PUBLIC_PATH, "bad").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(jsonPath("$.code").value(4000));
        mvc.perform(get(PUBLIC_PATH, 0).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(jsonPath("$.code").value(4001));
        verify(userAccountMapper, never()).selectOne(any());

        mvc.perform(get(PUBLIC_PATH, 42).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(jsonPath("$.code").value(4040));
        mvc.perform(get(ADMIN_PATH, 42).header(HttpHeaders.AUTHORIZATION, bearerForRole(UserConstant.ROLE_ADMIN)))
                .andExpect(jsonPath("$.code").value(4040));
    }

    /**
     * 验证管理员的创建、更新、删除和分页均要求管理员身份。
     */
    @Test
    void restrictsAdminManagementToAdmins() throws Exception {
        String user = bearerForRole(UserConstant.ROLE_USER);
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"NewUser"}
                                """))
                .andExpect(jsonPath("$.code").value(4030));
        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, user)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"id":42,"displayName":"New name"}
                                """))
                .andExpect(jsonPath("$.code").value(4030));
        mvc.perform(delete(ADMIN_PATH, 42).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(jsonPath("$.code").value(4030));
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, user).param("account", "Alice"))
                .andExpect(jsonPath("$.code").value(4030));
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
        verify(userAccountMapper, never()).update(any());
        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
        verify(userAccountMapper, never()).deleteById(42L);
        verify(userAccountMapper, never()).selectCount(any());
        verify(userAccountMapper, never()).selectPage(any(Page.class), any());
    }

    /**
     * 验证创建成功仅返回业务码，使用默认密码哈希并写入 VIP 角色和 UTC 到期时间。
     *
     * @param suppliedPassword 是否模拟旧客户端额外提交密码字段
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsVipWithDefaultPasswordAndChecksExpiry(boolean suppliedPassword) throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"Alice_01","platformRole":"VIP"}
                                """))
                .andExpect(jsonPath("$.code").value(4001));
        verify(userAccountMapper, never()).insert(any(UserAccount.class));

        var payload = json.createObjectNode().put("account", " Alice_01 ").put("platformRole", "VIP")
                .put("vipExpiresAt", "2026-12-01");
        if (suppliedPassword) {
            payload.put("password", "customPassword");
        }
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(content().json("{\"code\":200}", true));

        var inserted = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getAccount()).isEqualTo("Alice_01");
        assertThat(inserted.getValue().getStatus()).isEqualTo(UserConstant.STATUS_ACTIVE);
        assertThat(inserted.getValue().getPlatformRole()).isEqualTo(UserConstant.ROLE_VIP);
        assertThat(inserted.getValue().getVipExpiresAt()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(inserted.getValue().getPasswordHash()).startsWith("{pbkdf2-sha256-v1}");
        assertThat(passwordEncoder.matches("12345678", inserted.getValue().getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("customPassword", inserted.getValue().getPasswordHash())).isFalse();
        assertThat(inserted.getValue().getCreatedAt()).isNotNull();
        assertThat(inserted.getValue().getUpdateTime()).isEqualTo(inserted.getValue().getCreatedAt());
        assertThat(inserted.getValue().getCreatedAt().getNano() % 1_000_000).isZero();
        assertThat(inserted.getValue().getEditTime()).isEqualTo(inserted.getValue().getCreatedAt());
        verify(userAccountMapper, never()).selectOne(any());
        verifyNoInteractions(transactionManager);
    }

    /**
     * 验证管理员无法通过管理接口修改或删除自己的账号。
     */
    @Test
    void rejectsAdminSelfUpdateAndDeletion() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);

        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"id":7,"displayName":"New name"}
                                """))
                .andExpect(jsonPath("$.code").value(4030));
        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"id":7,"platformRole":"USER"}
                                """))
                .andExpect(jsonPath("$.code").value(4030));
        mvc.perform(delete(ADMIN_PATH, ACTOR_ID).header(HttpHeaders.AUTHORIZATION, admin)
                        .param("operatorId", "42"))
                .andExpect(jsonPath("$.code").value(4030));
        verify(userAccountMapper, never()).selectOne(any());
        verify(userAccountMapper, never()).update(any());
        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
        verify(userAccountMapper, never()).deleteById(ACTOR_ID);
    }

    /**
     * 验证直接更新仅返回成功业务码，空值和受保护字段均不写入，服务器生成更新时间。
     */
    @Test
    void promotesAnotherUserToAdmin() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        Instant before = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);

        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"id":42,"platformRole":"ADMIN","account":null,"displayName":null,
                                 "status":null,"vipExpiresAt":null,"createdAt":"2000-01-01T00:00:00Z",
                                 "editTime":"2000-01-01T00:00:00Z","updateTime":"2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"code\":200}", true));

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).updateById(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(42L);
        assertThat(saved.getValue().getPlatformRole()).isEqualTo(UserConstant.ROLE_ADMIN);
        assertThat(saved.getValue().getAccount()).isNull();
        assertThat(saved.getValue().getDisplayName()).isNull();
        assertThat(saved.getValue().getStatus()).isNull();
        assertThat(saved.getValue().getVipExpiresAt()).isNull();
        assertThat(saved.getValue().getCreatedAt()).isNull();
        assertThat(saved.getValue().getEditTime()).isNull();
        assertThat(saved.getValue().getPasswordHash()).isNull();
        assertThat(saved.getValue().getUpdateTime().toInstant(java.time.ZoneOffset.UTC))
                .isBetween(before, Instant.now());
        assertThat(saved.getValue().getUpdateTime().getNano() % 1_000_000).isZero();
        verifyNoInteractions(transactionManager);
        verify(userAccountMapper, never()).selectOne(any());
        verify(userAccountMapper, never()).selectList(any());
    }

    /**
     * 验证单独提交会员到期日期而不声明 VIP 身份时被拒绝。
     */
    @Test
    void rejectsVipExpiryWithoutVipRole() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        var payload = json.createObjectNode().put("id", 42).put("vipExpiresAt", "2027-01-01");

        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(jsonPath("$.code").value(4001));

        verify(userAccountMapper, never()).update(any(UserAccount.class), any());
    }

    /**
     * 验证同时声明 VIP 身份时，会员到期日期按原值保存。
     */
    @Test
    void copiesSubmittedFieldsWithVipExpiryDate() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(1);
        var payload = json.createObjectNode().put("id", 42).put("displayName", " New name ")
                .put("vipExpiresAt", "2027-01-01").put("platformRole", UserConstant.ROLE_VIP);

        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(content().json("{\"code\":200}", true));

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).updateById(saved.capture());
        assertThat(saved.getValue().getVipExpiresAt()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(saved.getValue().getDisplayName()).isEqualTo("New name");
        assertThat(saved.getValue().getPlatformRole()).isEqualTo(UserConstant.ROLE_VIP);
        assertThat(saved.getValue().getAccount()).isNull();
        assertThat(saved.getValue().getStatus()).isNull();
        verify(userAccountMapper, never()).selectOne(any());
        verifyNoInteractions(transactionManager);
    }

    /**
     * 验证直接更新影响行数为零时返回目标不存在。
     */
    @Test
    void returnsNotFoundWhenUpdateAffectsNoRows() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        when(userAccountMapper.updateById(any(UserAccount.class))).thenReturn(0);
        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"id":42,"displayName":"New name"}
                                """))
                .andExpect(jsonPath("$.code").value(4040));
        verify(userAccountMapper).updateById(any(UserAccount.class));
        verify(userAccountMapper, never()).selectOne(any());
    }

    /**
     * 验证直接更新仍将数据库唯一索引冲突映射为账号已占用。
     */
    @Test
    void returnsConflictWhenUpdateAccountIsOccupied() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        when(userAccountMapper.updateById(any(UserAccount.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate account"));
        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"id":42,"account":"Occupied"}
                                """))
                .andExpect(jsonPath("$.code").value(4090));
        verify(userAccountMapper, never()).selectOne(any());
    }

    /**
     * 验证请求体必须包含正整数 ID，且不能只提交 ID 或全空的资料字段。
     *
     * @param payload 待校验的更新请求体
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"displayName\":\"New name\"}",
            "{\"id\":null,\"displayName\":\"New name\"}",
            "{\"id\":0,\"displayName\":\"New name\"}",
            "{\"id\":-1,\"displayName\":\"New name\"}",
            "{\"id\":42}",
            "{\"id\":42,\"platformRole\":\"VIP\"}",
            "{\"id\":42,\"platformRole\":\"VIP\",\"vipExpiresAt\":null}",
            "{\"id\":42,\"account\":null,\"displayName\":null,\"platformRole\":null,\"status\":null,\"vipExpiresAt\":null}"
    })
    void rejectsInvalidUpdateBodies(String payload) throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        mvc.perform(patch("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));
        verify(userAccountMapper, never()).selectOne(any());
        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
    }

    /**
     * 验证通过 deleteById 执行逻辑删除，已删除或不存在的目标返回错误。
     */
    @Test
    void logicallyDeletesTargetAndRejectsMissingUser() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        when(userAccountMapper.deleteById(42L)).thenReturn(1, 0);

        mvc.perform(delete(ADMIN_PATH, 42).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotHaveJsonPath());
        mvc.perform(delete(ADMIN_PATH, 42).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.code").value(4040));
        verify(userAccountMapper, never()).update(any());
        verify(userAccountMapper, never()).selectOne(any());
        verify(userAccountMapper, never()).selectList(any());
    }

    /**
     * 验证 Page 参数传递、停用账号展示、字段范围及越界页的分页结果。
     */
    @Test
    void returnsAdminPagesWithoutPasswords() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        UserAccount target = targetUser();
        target.setStatus(UserConstant.STATUS_DISABLED);
        Page<UserAccount> firstPage = new Page<UserAccount>(1, 20, 1).setRecords(List.of(target));
        Page<UserAccount> emptyPage = new Page<>(2, 1, 1);
        when(userAccountMapper.selectPage(any(Page.class), any())).thenReturn(firstPage, emptyPage);

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].account").value("Alice_01"))
                .andExpect(jsonPath("$.data.records[0].status").value("DISABLED"))
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotHaveJsonPath());

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .param("page", "2").param("pageSize", "1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin).param("pageSize", "101"))
                .andExpect(jsonPath("$.code").value(4001));
        var pages = ArgumentCaptor.forClass(Page.class);
        verify(userAccountMapper, times(2)).selectPage(pages.capture(), any());
        assertThat(pages.getAllValues().get(0).getCurrent()).isEqualTo(1L);
        assertThat(pages.getAllValues().get(0).getSize()).isEqualTo(20L);
        assertThat(pages.getAllValues().get(1).getCurrent()).isEqualTo(2L);
        assertThat(pages.getAllValues().get(1).getSize()).isEqualTo(1L);
        verify(userAccountMapper, never()).selectCount(any());
        verifyNoInteractions(transactionManager);
    }

    /**
     * 验证账号搜索保留大小写并通过参数绑定将下划线按字面转义。
     *
     * @param account 首尾空白、单字符或历史账号长度的合法搜索片段
     */
    @ParameterizedTest
    @ValueSource(strings = {"  Alice_  ", "_", "A", "Abcdefghijklmnopqrstuvwxyz123456"})
    void bindsLiteralAccountSearch(String account) throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        when(userAccountMapper.selectPage(any(Page.class), any())).thenReturn(new Page<UserAccount>(1, 20, 0));

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin).param("account", account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());

        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userAccountMapper).selectPage(any(Page.class), query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("account LIKE", "ESCAPE '!'", "ORDER BY id DESC");
        assertThat(query.getValue().getParamNameValuePairs().values())
                .containsExactly("%" + account.strip().replace("_", "!_") + "%");
    }

    /**
     * 验证空白账号搜索沿用全量分页查询。
     *
     * @param account 空字符串或仅包含空白的搜索参数
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " \t "})
    void ignoresBlankAccountSearch(String account) throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        when(userAccountMapper.selectPage(any(Page.class), any())).thenReturn(new Page<UserAccount>(1, 20, 0));

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin).param("account", account))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userAccountMapper).selectPage(any(Page.class), query.capture());
        assertThat(query.getValue().getSqlSegment()).doesNotContain("LIKE");
    }

    /**
     * 验证非法字符和超过历史账号长度的搜索条件在执行查询前被拒绝。
     *
     * @param account 非法账号片段
     */
    @ParameterizedTest
    @ValueSource(strings = {"%", "a b", "中文", "a-b", "!", "a\\b", "a\nb",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void rejectsInvalidAccountSearch(String account) throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin).param("account", account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));
        verify(userAccountMapper, never()).selectPage(any(Page.class), any());
    }

    /**
     * 验证添加账号条件后仍沿用分页参数格式错误的业务码。
     */
    @Test
    void rejectsMalformedPaginationWithAccountSearch() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .param("account", "Alice").param("page", "invalid"))
                .andExpect(jsonPath("$.code").value(4000));
        verify(userAccountMapper, never()).selectPage(any(Page.class), any());
    }

    /**
     * 验证查询对象按字段含义组合匹配，并将带时区的时间范围转换为 UTC。
     */
    @Test
    void combinesUserQueryConditions() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        when(userAccountMapper.selectPage(any(Page.class), any())).thenReturn(new Page<UserAccount>(1, 20, 0));

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .param("id", "42").param("account", " Alice_ ").param("displayName", " Alice_100%! ")
                        .param("platformRole", " ADMIN ").param("status", " DISABLED ")
                        .param("createdAtStart", "2026-11-01T08:00:00+08:00")
                        .param("createdAtEnd", "2026-11-30T23:59:59Z"))
                .andExpect(jsonPath("$.code").value(200));

        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userAccountMapper).selectPage(any(Page.class), query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("id =", "account LIKE", "display_name LIKE",
                "platform_role =", "status =", "created_at >=", "created_at <=", "ORDER BY id DESC");
        assertThat(query.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(
                42L, "%Alice!_%", "%Alice!_100!%!!%", UserConstant.ROLE_ADMIN, UserConstant.STATUS_DISABLED,
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 30, 23, 59, 59));
    }

    /**
     * 验证继承的分页参数兼容空值默认行为，其他空白字段均不参与筛选。
     */
    @Test
    void defaultsEmptyPaginationAndIgnoresBlankConditions() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        when(userAccountMapper.selectPage(any(Page.class), any())).thenReturn(new Page<UserAccount>(1, 20, 0));

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .param("page", "").param("pageSize", "").param("id", "")
                        .param("account", " ").param("displayName", " ").param("platformRole", " ")
                        .param("status", " ").param("createdAtStart", "").param("createdAtEnd", ""))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userAccountMapper).selectPage(any(Page.class), query.capture());
        assertThat(query.getValue().getSqlSegment().strip()).isEqualTo("ORDER BY id DESC");
        assertThat(query.getValue().getParamNameValuePairs()).isEmpty();
    }

    /**
     * 验证查询对象的约束失败和类型格式错误沿用不同业务码。
     *
     * @param field 查询字段名
     * @param value 非法字段值
     * @param code 约束失败为 4001，类型格式错误为 4000
     */
    @ParameterizedTest
    @CsvSource({
            "id, 0, 4001", "id, -1, 4001", "id, invalid, 4000",
            "platformRole, ROOT, 4001", "platformRole, admin, 4001",
            "status, ENABLED, 4001", "status, active, 4001",
            "page, 0, 4001", "pageSize, 0, 4001", "pageSize, invalid, 4000",
            "createdAtStart, invalid, 4000", "createdAtEnd, invalid, 4000"
    })
    void rejectsInvalidQueryFields(String field, String value, int code) throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin).param(field, value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
        verify(userAccountMapper, never()).selectPage(any(Page.class), any());
    }

    /**
     * 验证创建时间上下限颠倒和超长昵称在查询前被拒绝。
     */
    @Test
    void rejectsInvalidCreatedAtRangeAndLongDisplayName() throws Exception {
        String admin = bearerForRole(UserConstant.ROLE_ADMIN);
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .param("createdAtStart", "2026-12-01T00:00:00Z")
                        .param("createdAtEnd", "2026-11-01T00:00:00Z"))
                .andExpect(jsonPath("$.code").value(4001));
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .param("displayName", "A".repeat(65)))
                .andExpect(jsonPath("$.code").value(4001));
        verify(userAccountMapper, never()).selectPage(any(Page.class), any());
    }

    /**
     * 验证 OpenAPI 文档中的用户接口、管理员 Bearer 鉴权及响应字段。
     */
    @Test
    void documentsDetailsAndPublicFields() throws Exception {
        mvc.perform(get("/v3/api-docs/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].get.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].post.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].patch.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].delete.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].patch").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.components.schemas.AdminUpdateUserRequest.required[0]").value("id"))
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].delete.parameters.length()").value(1))
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.parameters[?(@.name == 'account')]").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.parameters[?(@.name == 'page')].schema.default").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.parameters[?(@.name == 'pageSize')].schema.maximum").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.parameters[?(@.name == 'displayName')]").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.parameters[?(@.name == 'platformRole')]").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.parameters[?(@.name == 'status')]").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.parameters[?(@.name == 'createdAtStart')]").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.parameters[?(@.name == 'createdAtEnd')]").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].get.security[0].Authorization").exists())
                .andExpect(jsonPath("$.components.schemas.AdminUserVo.properties.account").exists())
                .andExpect(jsonPath("$.components.schemas.AdminUserVo.properties.passwordHash").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.components.schemas.AdminCreateUserRequest.properties.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.components.schemas.AdminUpdateUserRequest.properties.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.components.schemas.AdminUserPageVo.properties.records").exists())
                .andExpect(jsonPath("$.components.schemas.PublicUserVo.properties.account").exists())
                .andExpect(jsonPath("$.components.schemas.PublicUserVo.properties.id").doesNotHaveJsonPath());
        mvc.perform(get("/doc.html")).andExpect(status().isOk());
    }

    /**
     * 模拟指定角色的正常账号并签发真实 JWT。
     *
     * @param role 调用方的平台角色
     * @return 可放入 Authorization 请求头的令牌
     */
    private String bearerForRole(String role) {
        var token = JwtUtil.issueAccessToken(jwtEncoder, jwtProperties, ACTOR_ID, Instant.now());
        UserAccount actor = new UserAccount();
        actor.setId(ACTOR_ID);
        actor.setStatus(UserConstant.STATUS_ACTIVE);
        actor.setPlatformRole(role);
        when(userAccountMapper.selectById(ACTOR_ID)).thenReturn(actor);
        when(redisTemplate.hasKey("imagecloud:auth:revoked:" + token.getId())).thenReturn(false);
        return "Bearer " + token.getTokenValue();
    }

    /**
     * 构造含有公开和私有字段的用户资料。
     *
     * @return 用于检查响应字段范围的目标账号
     */
    private UserAccount targetUser() {
        UserAccount target = new UserAccount();
        target.setId(42L);
        target.setAccount("Alice_01");
        target.setDisplayName("Alice");
        target.setAvatarUrl("https://example.cos.ap-beijing.myqcloud.com/avatars/alice.png");
        target.setPasswordHash("test-hash");
        target.setPlatformRole(UserConstant.ROLE_USER);
        target.setStatus(UserConstant.STATUS_ACTIVE);
        target.setIsDelete(UserConstant.NOT_DELETED);
        target.setCreatedAt(LocalDateTime.of(2026, 11, 17, 2, 30));
        target.setUpdateTime(LocalDateTime.of(2026, 11, 17, 3, 30));
        return target;
    }
}
