-- 本文件维护各表的最终建表结构，后续结构调整直接修改对应 CREATE TABLE。

CREATE TABLE user_account (
    id BIGINT NOT NULL COMMENT '用户 ID，由应用侧雪花算法生成',
    account VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL COMMENT '登录账号，区分大小写',
    password_hash VARCHAR(255) NOT NULL COMMENT '带算法标识的密码哈希',
    display_name VARCHAR(12) DEFAULT NULL COMMENT '展示昵称',
    avatar_url VARCHAR(2048) DEFAULT NULL COMMENT '压缩头像公开固定 URL',
    avatar_original_url VARCHAR(2048) DEFAULT NULL COMMENT '头像原图公开固定 URL',
    gender TINYINT DEFAULT NULL COMMENT '性别，0 未设置，1 男，2 女',
    bio VARCHAR(128) DEFAULT NULL COMMENT '自我介绍',
    email VARCHAR(254) DEFAULT NULL COMMENT '公开联系邮箱',
    phone_number VARCHAR(21) DEFAULT NULL COMMENT '手机号，仅本人和管理员可见',
    birth_month VARCHAR(7) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '出生年月，YYYY-MM',
    platform_role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'USER' COMMENT '平台角色',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态',
    vip_expires_at DATE DEFAULT NULL COMMENT 'VIP 到期日期，到期当天仍然有效',
    is_delete TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，由数据库生成，秒精度',
    edit_time DATETIME DEFAULT (created_at) COMMENT '用户最近一次编辑时间，初始为创建时间，本地日期时间，秒精度',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次变更时间，由 MySQL 自动维护，本地日期时间，秒精度',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_account (account),
    CONSTRAINT chk_user_account_gender CHECK (gender IN (0, 1, 2)),
    CONSTRAINT chk_user_account_role CHECK (platform_role IN ('USER', 'VIP', 'ADMIN')),
    CONSTRAINT chk_user_account_vip_expiry CHECK (platform_role <> 'VIP' OR vip_expires_at IS NOT NULL),
    CONSTRAINT chk_user_account_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_user_account_is_delete CHECK (is_delete IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号';

-- 图片主表仅保存已提交的图片；创建时间由数据库生成，审核时间由应用写入本地日期时间，更新时间由 MySQL 维护，时间列统一秒精度。
CREATE TABLE image (
    id BIGINT NOT NULL COMMENT '图片 ID，由应用侧雪花算法生成',
    space_id BIGINT DEFAULT NULL COMMENT '所属空间 ID，为空表示不属于任何空间',
    uploader_id BIGINT NOT NULL COMMENT '上传用户 ID',
    name VARCHAR(16) NOT NULL COMMENT '图片展示名称',
    description VARCHAR(128) DEFAULT NULL COMMENT '图片说明',
    category VARCHAR(16) DEFAULT NULL COMMENT '图片大分类',
    tags JSON NOT NULL COMMENT '标签字符串数组，无标签时由应用写入空数组，每个标签最多 20 个字符',
    original_filename VARCHAR(255) NOT NULL COMMENT '首次上传的原始文件名',
    original_object_key VARCHAR(512) COLLATE utf8mb4_bin NOT NULL COMMENT '原图 COS 对象键，区分大小写',
    thumbnail_object_key VARCHAR(512) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '前端列表展示用压缩图 COS 对象键，区分大小写',
    format VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原图实际格式，由数据万象识别并统一为小写',
    file_size BIGINT NOT NULL COMMENT '原图大小，单位字节',
    width INT NOT NULL COMMENT '原图宽度，单位像素',
    height INT NOT NULL COMMENT '原图高度，单位像素',
    vip_only TINYINT NOT NULL DEFAULT 0 COMMENT 'VIP 查看限制，0 无限制，1 仅有效 VIP 可查看，仍需校验空间权限',
    like_count INT NOT NULL DEFAULT 0 COMMENT 'Redis 点赞数的落库快照，允许存在同步延迟',
    audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态，0 未审核，1 通过，2 不通过',
    audit_message VARCHAR(1024) DEFAULT NULL COMMENT '审核信息，未审核时为空',
    auditor_id BIGINT DEFAULT NULL COMMENT '审核人用户 ID，未审核时为空',
    audit_time DATETIME DEFAULT NULL COMMENT '审核时间，本地日期时间，未审核时为空，秒精度',
    is_delete TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，由数据库生成，秒精度',
    edit_time DATETIME DEFAULT (created_at) COMMENT '用户最近一次主动编辑时间，初始为创建时间，本地日期时间，秒精度',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次变更时间，由 MySQL 自动维护，本地日期时间，秒精度',

    PRIMARY KEY (id),
    KEY idx_image_space (space_id, is_delete, id),
    KEY idx_image_category (space_id, is_delete, category, id),
    KEY idx_image_original_object (original_object_key),
    CONSTRAINT chk_image_tags CHECK (JSON_TYPE(tags) = 'ARRAY'),
    CONSTRAINT chk_image_file_size CHECK (file_size > 0),
    CONSTRAINT chk_image_width CHECK (width > 0),
    CONSTRAINT chk_image_height CHECK (height > 0),
    CONSTRAINT chk_image_vip_only CHECK (vip_only IN (0, 1)),
    CONSTRAINT chk_image_like_count CHECK (like_count >= 0),
    CONSTRAINT chk_image_audit_status CHECK (audit_status IN (0, 1, 2)),
    CONSTRAINT chk_image_is_delete CHECK (is_delete IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图片主表';
