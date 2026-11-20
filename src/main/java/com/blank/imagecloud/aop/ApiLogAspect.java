package com.blank.imagecloud.aop;

import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.exception.BusinessException;
import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 为 REST 接口记录调用和结果。参数校验与认证拦截失败由全局异常处理器记录。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLogAspect {
    /**
     * 记录接口执行前后的请求上下文、业务结果及耗时，异常继续交由原有流程处理。
     *
     * @param joinPoint 当前控制器方法的执行连接点
     * @return 控制器方法原本返回的结果
     * @throws Throwable 控制器或角色鉴权抛出的原始异常
     */
    @Around("execution(public * com.blank.imagecloud.controller..*(..))"
            + " && @within(org.springframework.web.bind.annotation.RestController)")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = servletAttributes.getRequest();
        String method = request.getMethod();
        // 使用匹配到的接口方法标识定位业务，避免记录请求参数和响应数据。
        String action = joinPoint.getSignature().toShortString();
        String path = request.getRequestURI();
        Long userId = CurrentUserContext.getUserId();
        long startedAt = System.nanoTime();
        log.info("API request started: method={}, path={}, action={}, userId={}", method, path, action, userId);
        try {
            Object result = joinPoint.proceed();
            Integer code = result instanceof Response<?> response ? response.getCode() : null;
            log.info("API request completed: method={}, path={}, action={}, userId={}, code={}, elapsedMs={}",
                    method, path, action, userId, code, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            return result;
        } catch (Throwable exception) {
            int code = exception instanceof BusinessException businessException
                    ? businessException.getErrorCode().getCode() : ErrorCode.INTERNAL_ERROR.getCode();
            // 原始错误信息和堆栈由全局异常处理器输出，此处记录调用结果和异常类型。
            log.info("API request failed: method={}, path={}, action={}, userId={}, code={}, elapsedMs={}, exceptionType={}",
                    method, path, action, userId, code, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }
}
