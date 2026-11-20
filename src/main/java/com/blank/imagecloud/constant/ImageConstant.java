package com.blank.imagecloud.constant;

/** 图片字段约束、审核状态和存储相关常量。 */
public final class ImageConstant {

    /** 图片展示名称上限，与 image.name 的 VARCHAR(16) 一致。 */
    public static final int NAME_MAX_LENGTH = 16;

    /** 图片说明上限，与 image.description 的 VARCHAR(128) 一致。 */
    public static final int DESCRIPTION_MAX_LENGTH = 128;

    /** 图片大分类上限，与 image.category 的 VARCHAR(16) 一致。 */
    public static final int CATEGORY_MAX_LENGTH = 16;

    /** 单个标签的字符上限，与建表注释约定一致。 */
    public static final int TAG_MAX_LENGTH = 20;

    /** 审核状态：未审核。 */
    public static final int AUDIT_STATUS_PENDING = 0;

    /** 审核状态：审核通过。管理员添加的图片直接使用该状态。 */
    public static final int AUDIT_STATUS_APPROVED = 1;

    /** 审核状态：审核不通过。 */
    public static final int AUDIT_STATUS_REJECTED = 2;

    /** 审核信息上限，与 image.audit_message 的 VARCHAR(1024) 一致。 */
    public static final int AUDIT_MESSAGE_MAX_LENGTH = 1024;

    /** VIP 查看限制：无限制。 */
    public static final int VIP_ONLY_DISABLED = 0;

    /** 图片未关联空间时使用的默认对象存储前缀。 */
    public static final String DEFAULT_STORAGE_PREFIX = "images/";

    /** 关联空间时使用的对象存储前缀模板，参数为空间 ID。 */
    public static final String SPACE_STORAGE_PREFIX = "spaces/%d/";

    private ImageConstant() {
    }
}
