package com.blank.imagecloud.constant;

/** 用户字段约束、角色和账号状态相关常量。 */
public final class UserConstant {
    public static final String ACCOUNT_PATTERN = "[A-Za-z0-9_]{3,16}";
    /** 账号搜索允许单字符片段，并兼容最长 32 位的历史账号。 */
    public static final String ACCOUNT_SEARCH_PATTERN = "[A-Za-z0-9_]{1,32}";
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 32;
    /** 管理员创建账号时使用的初始密码，由密码编码器生成哈希后保存。 */
    public static final String DEFAULT_PASSWORD = "12345678";
    public static final int DISPLAY_NAME_MAX_LENGTH = 64;

    public static final String ROLE_USER = "USER";
    public static final String ROLE_VIP = "VIP";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_PATTERN = ROLE_USER + "|" + ROLE_VIP + "|" + ROLE_ADMIN;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_PATTERN = STATUS_ACTIVE + "|" + STATUS_DISABLED;

    public static final int NOT_DELETED = 0;

    private UserConstant() {
    }
}
