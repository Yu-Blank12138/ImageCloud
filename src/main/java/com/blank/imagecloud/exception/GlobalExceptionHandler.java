package com.blank.imagecloud.exception;

import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.response.Response;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
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


    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusinessException(BusinessException exception, HttpServletResponse response) {
        ErrorCode errorCode = exception.getErrorCode();
        String message = exception.getMessage();
        log.warn("Business exception (code={}): {}", errorCode.getCode(), message);
        if (response.isCommitted()) {
            throw exception;
        }
        return ResultUtil.error(errorCode, message);
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception exception, HttpServletResponse response) throws Exception {
        if (response.isCommitted()
                || exception instanceof AsyncRequestNotUsableException
                || AnnotatedElementUtils.hasAnnotation(exception.getClass(), ResponseStatus.class)) {
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
                throw exception;
            }
        }

        if (errorCode == ErrorCode.INTERNAL_ERROR) {
            log.error("Unhandled MVC exception", exception);
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

    private boolean isRequestValidationError(Exception exception) {
        return exception instanceof MethodArgumentNotValidException
                || exception instanceof HandlerMethodValidationException
                || exception instanceof MissingRequestValueException
                || exception instanceof MissingServletRequestPartException;
    }
}
