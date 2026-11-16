package com.blank.imagecloud.exception;

import java.io.Serial;
import java.util.Objects;

/**
 * 携带业务错误码的运行时异常。
 */
public class BusinessException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(Objects.requireNonNullElse(message, errorCode.getMessage()));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
