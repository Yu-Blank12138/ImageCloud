package com.blank.imagecloud.interceptor;

import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.constant.AuthConstant;
import com.blank.imagecloud.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在业务方法执行前独立校验请求中的 Bearer JWT。 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements AsyncHandlerInterceptor {
    private static final Pattern BEARER_HEADER = Pattern.compile("^Bearer +([^\\s,]+)$", Pattern.CASE_INSENSITIVE);

    private final JwtDecoder jwtDecoder;
    private final StringRedisTemplate redisTemplate;

    /**
     * 验证请求令牌并绑定操作者，跨域预检直接交由 MVC 处理。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring MVC 匹配到的处理器
     * @return 请求可以继续进入 MVC 处理流程时返回 true
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUserContext.clear();

        // 跨域预检交由现有 CORS 配置处理。
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        Jwt token = verifyToken(request);
        Long userId = Long.valueOf(token.getSubject());
        verifyNotRevoked(token.getId());
        // 会话查询和注销读取已验证令牌，具体账号资料由业务流程查询。
        request.setAttribute(AuthConstant.CURRENT_TOKEN_ATTRIBUTE, token);
        CurrentUserContext.setUserId(userId);
        return true;
    }

    /**
     * 严格读取单个 Bearer 请求头，并验证签名、签发者、受众和令牌声明。
     *
     * @param request 当前 HTTP 请求
     * @return 已通过配置中的解码器验证的 JWT
     */
    private Jwt verifyToken(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        String header = headers.hasMoreElements() ? headers.nextElement() : null;
        ThrowUtils.throwIf(headers.hasMoreElements(), ErrorCode.INVALID_ACCESS_TOKEN);
        ThrowUtils.throwIf(!StringUtils.hasText(header), ErrorCode.AUTHENTICATION_REQUIRED);
        Matcher matcher = BEARER_HEADER.matcher(header.strip());
        ThrowUtils.throwIf(!matcher.matches(), ErrorCode.INVALID_ACCESS_TOKEN);
        try {
            return jwtDecoder.decode(matcher.group(1));
        } catch (JwtException exception) {
            ThrowUtils.throwException(ErrorCode.INVALID_ACCESS_TOKEN, null);
            return null;
        }
    }

    /**
     * 确认令牌没有被撤销。Redis 结果无法确认时拒绝请求。
     *
     * @param tokenId 已验证 JWT 的 jti
     */
    private void verifyNotRevoked(String tokenId) {
        Boolean revoked = null;
        try {
            revoked = redisTemplate.hasKey(AuthConstant.REVOKED_TOKEN_KEY_PREFIX + tokenId);
        } catch (DataAccessException ignored) {
            // 将撤销记录查询失败交给统一业务异常处理。
        }
        ThrowUtils.throwIf(revoked == null, ErrorCode.SERVICE_UNAVAILABLE);
        ThrowUtils.throwIf(Boolean.TRUE.equals(revoked), ErrorCode.INVALID_ACCESS_TOKEN);
    }

    /**
     * 在请求结束时清理当前线程的操作者。
     *
     * @param request 已处理的 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring MVC 匹配到的处理器
     * @param exception 处理期间未解析的异常，可为空
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        CurrentUserContext.clear();
    }

    /**
     * MVC 启动异步处理时，在请求线程返回线程池前清理操作者。
     *
     * @param request 开始异步处理的 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler Spring MVC 匹配到的处理器
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUserContext.clear();
    }
}
