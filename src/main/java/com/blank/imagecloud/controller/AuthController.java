package com.blank.imagecloud.controller;

import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.constant.AuthConstant;
import com.blank.imagecloud.models.dto.auth.LoginRequest;
import com.blank.imagecloud.models.dto.auth.RegisterRequest;
import com.blank.imagecloud.response.Response;
import com.blank.imagecloud.service.AuthService;
import com.blank.imagecloud.models.vo.LoginSessionVo;
import com.blank.imagecloud.models.vo.LoginVo;
import com.blank.imagecloud.models.vo.RegisterVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对外提供账号认证相关的 REST 接口。 */
@Tag(name = "账号认证")
@SecurityScheme(name = AuthController.BEARER_AUTH_SCHEME, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    /** Knife4j 4.5.0 会将 Bearer 安全方案的标识用作请求头名称。 */
    static final String BEARER_AUTH_SCHEME = HttpHeaders.AUTHORIZATION;

    private final AuthService authService;

    /**
     * 接收匿名注册请求，创建普通账号。
     *
     * @param request 账号、密码和可选昵称
     * @return 包含新用户 ID 和登录账号的成功响应
     */
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "注册账号", description = "创建普通用户账号。")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示成功，4000 表示请求格式错误，4001 表示字段校验失败，"
                    + "4090 表示账号已被占用，4150 表示媒体类型不受支持，5000 表示服务端错误。")
    public Response<RegisterVo> register(@Valid @RequestBody RegisterRequest request) {
        return ResultUtil.ok(authService.register(request));
    }

    /**
     * 完成密码登录并返回访问令牌，禁止缓存包含令牌的响应。
     *
     * @param request 登录账号和密码
     * @param response 用于设置缓存控制响应头的 Servlet 响应
     * @return 包含访问令牌、有效期和当前用户资料的成功响应
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "账号登录", description = "校验账号和密码，向有效账号签发 RS256 JWT。")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示成功，4000 表示请求格式错误，4001 表示字段校验失败，"
                    + "4011 表示登录凭据错误，4150 表示媒体类型不受支持，5000 表示服务端错误。")
    public Response<LoginVo> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginVo result = authService.login(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(result);
    }

    /**
     * 查询当前令牌的登录态并返回最新用户资料。
     *
     * @param token 拦截器已验证的当前令牌
     * @param response 用于禁止缓存登录态的 Servlet 响应
     * @return 包含当前用户资料和令牌到期时间的成功响应
     */
    @GetMapping(value = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "获取当前登录态", security = @SecurityRequirement(name = BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示有有效登录态，4010 表示缺少令牌，"
                    + "4012 表示令牌或账号失效，5030 表示暂无法确认撤销状态。")
    public Response<LoginSessionVo> getSession(
            @Parameter(hidden = true) @RequestAttribute(AuthConstant.CURRENT_TOKEN_ATTRIBUTE) Jwt token,
            HttpServletResponse response) {
        LoginSessionVo session = authService.getSession(CurrentUserContext.requireUserId(), token.getExpiresAt());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(session);
    }

    /**
     * 注销请求携带的访问令牌。
     *
     * @param token 拦截器已验签的待撤销令牌
     * @return 表示撤销已确认的成功响应
     */
    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "注销当前登录态", security = @SecurityRequirement(name = BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示令牌已撤销，4010 表示缺少令牌，"
                    + "4012 表示令牌已撤销、无效或过期，5030 表示暂无法完成撤销。")
    public Response<Void> logout(
            @Parameter(hidden = true) @RequestAttribute(AuthConstant.CURRENT_TOKEN_ATTRIBUTE) Jwt token) {
        authService.logout(token);
        return ResultUtil.ok();
    }
}
