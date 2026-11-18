package com.blank.imagecloud.common;

import com.blank.imagecloud.exception.BusinessException;
import com.blank.imagecloud.exception.ErrorCode;

/**
 * 统一抛出业务异常。
 */
public final class ThrowUtils {
    private ThrowUtils() {
    }

    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, errorCode, null);
    }

    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        if (condition) {
            throwException(errorCode, message);
        }
    }

    /**
     * 在已确定失败的分支中直接抛出业务异常。
     *
     * @param errorCode 业务错误码
     * @param message 自定义错误提示，为空时使用错误码的默认提示
     * @throws BusinessException 携带所选错误码和错误提示
     */
    public static void throwException(ErrorCode errorCode, String message) {
        throw new BusinessException(errorCode, message);
    }
}
