-- 项目建表语句按执行顺序追加到本文件。已有数据库只执行新增语句。
-- 已用旧版脚本建表的数据库，通过以下语句更新账号比较规则：
-- ALTER TABLE user_account MODIFY COLUMN account VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
--     NOT NULL COMMENT '登录账号，区分大小写';

CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    account VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL COMMENT '登录账号，区分大小写',
    password_hash VARCHAR(255) NOT NULL COMMENT '带算法标识的密码哈希',
    display_name VARCHAR(64) DEFAULT NULL COMMENT '展示昵称',
    avatar_url VARCHAR(2048) DEFAULT NULL COMMENT '头像 COS 对象原始 URL',
    platform_role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'USER' COMMENT '平台角色',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态',
    vip_expires_at DATETIME(3) DEFAULT NULL COMMENT 'VIP 到期时间，UTC',
    is_delete TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    edit_time DATETIME(3) DEFAULT NULL COMMENT '用户最近一次自行修改账号信息的时间，UTC',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '账号记录最近一次变更的时间，UTC',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_account (account),
    CONSTRAINT chk_user_account_role CHECK (platform_role IN ('USER', 'VIP', 'ADMIN')),
    CONSTRAINT chk_user_account_vip_expiry CHECK (platform_role <> 'VIP' OR vip_expires_at IS NOT NULL),
    CONSTRAINT chk_user_account_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_user_account_is_delete CHECK (is_delete IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

-- 2026-11-18 起由应用服务器写入 UTC 毫秒时间，保留已有数据并移除数据库自动时间。
ALTER TABLE user_account
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '创建时间，由应用服务器维护，UTC',
    MODIFY COLUMN edit_time DATETIME(3) DEFAULT NULL COMMENT '用户最近一次自行修改时间，由应用服务器维护，UTC',
    MODIFY COLUMN update_time DATETIME(3) NOT NULL COMMENT '最近一次变更时间，由应用服务器维护，UTC';

-- 2026-11-18 调整新账号的编辑时间默认值，并恢复 MySQL 自动维护更新时间。
ALTER TABLE user_account
    MODIFY COLUMN edit_time DATETIME(3) DEFAULT (created_at)
        COMMENT '用户最近一次自行修改时间，初始为创建时间，UTC',
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近一次变更时间，由 MySQL 自动维护，UTC';
