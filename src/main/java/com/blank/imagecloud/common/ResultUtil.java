package com.blank.imagecloud.common;

import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.response.Response;

import java.util.Objects;

/**
 * 构建 REST API 的响应体。
 */
public final class ResultUtil {
    private ResultUtil() {
    }

    public static Response<Void> ok() {
        return ok(null);
    }

    public static <T> Response<T> ok(T data) {
        return new Response<>(200, data);
    }

    public static <T> Response<T> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }

    public static <T> Response<T> error(ErrorCode errorCode, String message) {
        return new Response<>(errorCode.getCode(), null, Objects.requireNonNullElse(message, errorCode.getMessage()));
    }
}
