package com.blank.imagecloud.constant;

/** 请求认证和注销使用的服务端属性及 Redis 键。 */
public final class AuthConstant {
    /** 通过验签和撤销检查的当前请求令牌。 */
    public static final String CURRENT_TOKEN_ATTRIBUTE = "com.blank.imagecloud.currentToken";
    /** 令牌撤销记录使用此前缀加 JWT 的 jti。 */
    public static final String REVOKED_TOKEN_KEY_PREFIX = "imagecloud:auth:revoked:";

    /**
     * 防止实例化认证常量类。
     */
    private AuthConstant() {
    }
}
