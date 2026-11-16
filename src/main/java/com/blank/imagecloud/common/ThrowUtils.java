package com.blank.imagecloud.common;

import com.blank.imagecloud.exception.BusinessException;
import com.blank.imagecloud.exception.ErrorCode;

/**
 * 条件成立时抛出业务异常。
 */
public final class ThrowUtils {
    private ThrowUtils() {
    }

    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, errorCode, null);
    }

    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        if (condition) {
            throw new BusinessException(errorCode, message);
        }
    }
}
