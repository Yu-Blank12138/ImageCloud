package com.blank.imagecloud.exception;

import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 记录业务失败的请求上下文，并沿用业务错误码构建响应。
     *
     * @param exception 携带错误码和提示的业务异常
     * @param request 用于读取请求方法和路径的当前请求
     * @param response 用于判断响应是否已经提交的当前响应
     * @return 包含业务错误码和提示的响应
     */
    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusinessException(BusinessException exception, HttpServletRequest request,
                                                 HttpServletResponse response) {
        ErrorCode errorCode = exception.getErrorCode();
        String message = exception.getMessage();
        log.warn("Business exception: method={}, path={}, userId={}, code={}, message={}",
                request.getMethod(), request.getRequestURI(), CurrentUserContext.getUserId(),
                errorCode.getCode(), message);
        if (response.isCommitted()) {
            throw exception;
        }
        return ResultUtil.error(errorCode, message);
    }

    /**
     * 记录请求校验及未预期异常，保留原有错误映射和继续抛出的处理方式。
     *
     * @param exception MVC 处理过程中产生的异常
     * @param request 用于读取请求方法和路径的当前请求
     * @param response 用于判断响应是否已经提交的当前响应
     * @return 按既有规则映射的错误响应
     * @throws Exception 需要交由后续异常解析器或容器处理时继续抛出
     */
    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception exception, HttpServletRequest request,
                                         HttpServletResponse response) throws Exception {
        if (response.isCommitted()
                || exception instanceof AsyncRequestNotUsableException
                || AnnotatedElementUtils.hasAnnotation(exception.getClass(), ResponseStatus.class)) {
            log.warn("MVC exception delegated: method={}, path={}, userId={}, exceptionType={}",
                    request.getMethod(), request.getRequestURI(), CurrentUserContext.getUserId(),
                    exception.getClass().getSimpleName());
            throw exception;
        }

        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        if (exception instanceof HttpMessageNotReadableException
                || exception instanceof MethodArgumentTypeMismatchException) {
            errorCode = ErrorCode.INVALID_REQUEST;
        } else if (exception instanceof ErrorResponse mvcError) {
            errorCode = resolveMvcErrorCode(exception, mvcError.getStatusCode().value());
            if (errorCode == null) {
                // 未约定业务码的 HTTP 状态交给 Spring MVC 原有的异常解析器处理。
                log.warn("MVC exception delegated: method={}, path={}, userId={}, status={}, exceptionType={}",
                        request.getMethod(), request.getRequestURI(), CurrentUserContext.getUserId(),
                        mvcError.getStatusCode().value(), exception.getClass().getSimpleName());
                throw exception;
            }
        }

        if (errorCode == ErrorCode.INTERNAL_ERROR) {
            log.error("Unhandled MVC exception: method={}, path={}, userId={}, code={}",
                    request.getMethod(), request.getRequestURI(), CurrentUserContext.getUserId(),
                    errorCode.getCode(), exception);
        } else {
            // 参数异常的原始消息可能包含密码等输入值，仅记录异常类型和统一错误提示。
            log.warn("MVC request failed: method={}, path={}, userId={}, code={}, exceptionType={}, message={}",
                    request.getMethod(), request.getRequestURI(), CurrentUserContext.getUserId(),
                    errorCode.getCode(), exception.getClass().getSimpleName(), errorCode.getMessage());
        }
        return ResultUtil.error(errorCode);
    }

    private ErrorCode resolveMvcErrorCode(Exception exception, int status) {
        return switch (status) {
            case 400 -> isRequestValidationError(exception) ? ErrorCode.VALIDATION_FAILED : ErrorCode.INVALID_REQUEST;
            case 401 -> ErrorCode.AUTHENTICATION_REQUIRED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.CONFLICT;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 500 -> ErrorCode.INTERNAL_ERROR;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> null;
        };
    }

    /**
     * 区分约束校验失败与查询对象绑定时的类型格式错误。
     *
     * @param exception MVC 参数处理异常
     * @return 字段约束失败时为 true，类型绑定失败时为 false
     */
    private boolean isRequestValidationError(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            // 查询对象的类型转换失败沿用格式错误码，保留原有分页接口的契约。
            return validationException.getBindingResult().getFieldErrors().stream()
                    .noneMatch(FieldError::isBindingFailure);
        }
        return exception instanceof HandlerMethodValidationException
                || exception instanceof MissingRequestValueException
                || exception instanceof MissingServletRequestPartException;
    }
}
