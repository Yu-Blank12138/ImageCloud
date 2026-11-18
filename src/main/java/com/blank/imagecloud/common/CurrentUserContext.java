package com.blank.imagecloud.common;

import com.blank.imagecloud.exception.ErrorCode;

/** 保存当前请求线程上通过认证的操作者 ID。 */
public final class CurrentUserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /**
     * 防止实例化线程上下文工具类。
     */
    private CurrentUserContext() {
    }

    /**
     * 将认证通过的操作者绑定到当前请求线程。
     *
     * @param userId 从已验证 JWT 取得的用户 ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 查询当前线程上绑定的操作者。
     *
     * @return 已绑定的用户 ID，未认证时返回 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 取得当前操作者，供要求登录的业务操作使用。
     *
     * @return 经过认证的操作者 ID
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.AUTHENTICATION_REQUIRED);
        return userId;
    }

    /**
     * 移除当前线程上的操作者，避免服务器线程复用时串号。
     */
    public static void clear() {
        USER_ID.remove();
    }
}
