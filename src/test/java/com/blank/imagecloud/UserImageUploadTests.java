package com.blank.imagecloud;

import com.blank.imagecloud.config.CosConfig;
import com.blank.imagecloud.config.JwtProperties;
import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.exception.BusinessException;
import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.mapper.UserAccountMapper;
import com.blank.imagecloud.models.entity.UserAccount;
import com.blank.imagecloud.models.vo.image.ImageUploadVo;
import com.blank.imagecloud.utils.CosUtils;
import com.blank.imagecloud.utils.JwtUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.CannedAccessControlList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 验证用户头像上传的认证、工具复用和固定展示 URL 契约。 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
class UserImageUploadTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private CosConfig cosConfig;
    @MockitoBean private UserAccountMapper mapper;
    @MockitoBean private StringRedisTemplate redis;
    @MockitoBean private CosUtils cosUtils;
    @MockitoBean private COSClient cosClient;

    /**
     * 验证所有头像使用统一前缀，展示图公开读并返回无签名参数的固定 URL。
     */
    @Test
    void returnsDisplayUrlForCurrentUserUpload() throws Exception {
        String auth = authenticate(true);
        var image = new ImageUploadVo();
        image.setOriginalObjectKey("avatars/original.png");
        image.setThumbnailObjectKey("avatars/original_compressed.webp");
        when(cosUtils.uploadImage(any(), eq("avatars/"))).thenReturn(image);
        String url = "https://example.cos.ap-beijing.myqcloud.com/avatars/original_compressed.webp";
        when(cosClient.getObjectUrl(cosConfig.getBucket(), image.getThumbnailObjectKey()))
                .thenReturn(URI.create(url).toURL());
        String originalUrl = "https://example.cos.ap-beijing.myqcloud.com/avatars/original.png";
        when(cosClient.getObjectUrl(cosConfig.getBucket(), image.getOriginalObjectKey()))
                .thenReturn(URI.create(originalUrl).toURL());
        mvc.perform(multipart("/api/images/avatars").file(file()).header(HttpHeaders.AUTHORIZATION, auth)
                        .param("userId", "999").param("uploadPrefix", "elsewhere"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.avatarUrl").value(url))
                .andExpect(jsonPath("$.data.avatarOriginalUrl").value(originalUrl))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verify(cosUtils).uploadImage(any(), eq("avatars/"));
        var calls = inOrder(cosClient);
        calls.verify(cosClient).setObjectAcl(cosConfig.getBucket(), image.getThumbnailObjectKey(),
                CannedAccessControlList.PublicRead);
        calls.verify(cosClient).getObjectUrl(cosConfig.getBucket(), image.getThumbnailObjectKey());
        verify(cosClient).setObjectAcl(cosConfig.getBucket(), image.getOriginalObjectKey(),
                CannedAccessControlList.PublicRead);
        assertThat(URI.create(url).getRawQuery()).isNull();
    }

    /**
     * 验证匿名和已停用账号均不能发起云端上传。
     */
    @Test
    void rejectsAnonymousAndDisabledAccounts() throws Exception {
        mvc.perform(multipart("/api/images/avatars").file(file()))
                .andExpect(jsonPath("$.code").value(4010));
        String auth = authenticate(false);
        mvc.perform(multipart("/api/images/avatars").file(file()).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(4012));
        verifyNoInteractions(cosUtils, cosClient);
    }

    /**
     * 验证缺少文件及上传工具返回的格式错误按已有错误码处理。
     */
    @Test
    void rejectsMissingAndInvalidFile() throws Exception {
        String auth = authenticate(true);
        mvc.perform(multipart("/api/images/avatars").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(4001));
        verifyNoInteractions(cosUtils, cosClient);
        when(cosUtils.uploadImage(any(), any())).thenThrow(new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        mvc.perform(multipart("/api/images/avatars").file(file()).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(4150));
        verifyNoInteractions(cosClient);
    }

    /**
     * 验证头像公开读设置失败时返回服务暂不可用，并避免返回不可访问地址。
     */
    @Test
    void handlesPublishingFailure() throws Exception {
        String auth = authenticate(true);
        var image = new ImageUploadVo();
        image.setThumbnailObjectKey("avatars/compressed.webp");
        when(cosUtils.uploadImage(any(), any())).thenReturn(image);
        doThrow(new CosClientException("ACL update failed")).when(cosClient)
                .setObjectAcl(cosConfig.getBucket(), image.getThumbnailObjectKey(), CannedAccessControlList.PublicRead);
        mvc.perform(multipart("/api/images/avatars").file(file()).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(5030))
                .andExpect(jsonPath("$.data").doesNotHaveJsonPath());
        verify(cosClient, never()).getObjectUrl(any(String.class), any(String.class));
    }

    /**
     * 验证原图直接读取用户表中的原始头像地址。
     */
    @Test
    void returnsOriginalUrlFromUserAccount() throws Exception {
        String auth = authenticate(true);
        var target = new UserAccount();
        String url = "https://example.cos.ap-beijing.myqcloud.com/avatars/original.jpeg";
        target.setAvatarOriginalUrl(url);
        when(mapper.selectOne(any())).thenReturn(target);
        mvc.perform(get("/api/images/avatars/99/original").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(url));
        verifyNoInteractions(cosUtils, cosClient);
    }

    /**
     * 验证原图查询要求登录，空原图地址及无效 ID 返回既有业务错误码。
     */
    @Test
    void rejectsUnavailableOriginalAvatar() throws Exception {
        mvc.perform(get("/api/images/avatars/99/original")).andExpect(jsonPath("$.code").value(4010));
        String auth = authenticate(true);
        mvc.perform(get("/api/images/avatars/0/original").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(4001));
        mvc.perform(get("/api/images/avatars/bad/original").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(4000));
        mvc.perform(get("/api/images/avatars/99/original").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(4040));
        var target = new UserAccount();
        when(mapper.selectOne(any())).thenReturn(target);
        mvc.perform(get("/api/images/avatars/99/original").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(4040));
        target.setAvatarUrl("https://example.com/unknown.png");
        mvc.perform(get("/api/images/avatars/99/original").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.code").value(4040));
        verifyNoInteractions(cosUtils, cosClient);
    }

    /**
     * 验证 OpenAPI 描述 multipart 文件参数及原图查询。
     */
    @Test
    void documentsMultipartUpload() throws Exception {
        mvc.perform(get("/v3/api-docs/default"))
                .andExpect(jsonPath("$.paths['/api/images/avatars'].post.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/images/avatars/{id}/original'].get.security[0].Authorization").exists())
                .andExpect(jsonPath("$.paths['/api/images/avatars'].post.requestBody.content['multipart/form-data'].schema").exists());
        mvc.perform(get("/doc.html")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    /**
     * 构造当前用户的实际签名 JWT 并模拟正常的撤销检查。
     *
     * @param active 账号是否为正常状态
     * @return Authorization 请求头值
     */
    private String authenticate(boolean active) {
        var user = new UserAccount();
        user.setId(42L);
        user.setPlatformRole(UserConstant.ROLE_USER);
        user.setStatus(active ? UserConstant.STATUS_ACTIVE : UserConstant.STATUS_DISABLED);
        when(mapper.selectById(42L)).thenReturn(user);
        var jwt = JwtUtil.issueAccessToken(encoder, jwtProperties, 42L, Instant.now());
        when(redis.hasKey("imagecloud:auth:revoked:" + jwt.getId())).thenReturn(false);
        return "Bearer " + jwt.getTokenValue();
    }

    /**
     * 创建用于 MVC 调用的内存上传文件。
     *
     * @return 名为 file 的 multipart 图像参数
     */
    private MockMultipartFile file() {
        return new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2, 3});
    }
}
