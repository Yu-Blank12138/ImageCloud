package com.blank.imagecloud.models.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图片主表实体，记录图片信息、原图及展示用压缩图对象引用。
 * 标签通过 JSON 类型处理器读写，访问权限与元数据校验由业务服务负责。
 */
@Getter
@Setter
@TableName(value = "image", autoResultMap = true)
public class Image {

    /**
     * 图片主键，由应用侧雪花算法生成。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属空间 ID，为空表示图片不属于任何空间；访问权限通过空间服务校验。
     */
    @TableField("space_id")
    private Long spaceId;

    /**
     * 首次上传图片的用户 ID，由服务端从登录身份取得。
     */
    @TableField("uploader_id")
    private Long uploaderId;

    /**
     * 图片展示名称，最多 16 个字符。
     */
    @TableField("name")
    private String name;

    /**
     * 图片说明，最多 128 个字符，未填写时为空。
     */
    @TableField("description")
    private String description;

    /**
     * 图片大分类，最多 16 个字符，未分类时为空。
     */
    @TableField("category")
    private String category;

    /**
     * 标签字符串列表，无标签时写入空列表，每个标签最多 20 个字符。
     * 业务服务负责去除首尾空白、过滤空标签并去重。
     */
    @TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 首次上传时的原始文件名，最多 255 个字符。
     */
    @TableField("original_filename")
    private String originalFilename;

    /**
     * 原图 COS 对象键，由服务端生成，上传成功后保持不变。
     */
    @TableField("original_object_key")
    private String originalObjectKey;

    /**
     * 前端列表展示用压缩图的 COS 对象键，尚未生成时为空。
     */
    @TableField("thumbnail_object_key")
    private String thumbnailObjectKey;

    /**
     * 原图实际格式，由数据万象识别并统一为小写，最多 10 个字符。
     */
    @TableField("format")
    private String format;

    /**
     * 原图文件大小，单位字节，必须大于零。
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 原图宽度，单位像素，必须大于零。
     */
    @TableField("width")
    private Integer width;

    /**
     * 原图高度，单位像素，必须大于零。
     */
    @TableField("height")
    private Integer height;

    /**
     * VIP 查看限制，0 表示无 VIP 限制，1 表示仅有效 VIP 可查看，仍需校验空间权限。
     */
    @TableField("vip_only")
    private Integer vipOnly;

    /**
     * Redis 点赞数量的落库快照，默认为零，允许存在同步延迟。
     */
    @TableField("like_count")
    private Integer likeCount;

    /**
     * 审核状态，0 表示未审核，1 表示审核通过，2 表示审核不通过，新图片默认为未审核。
     */
    @TableField("audit_status")
    private Integer auditStatus;

    /**
     * 审核信息，最多 1024 个字符，未审核时为空，用于说明审核结论或拒绝原因。
     */
    @TableField("audit_message")
    private String auditMessage;

    /**
     * 审核人用户 ID，由服务端从审核操作者的登录身份取得，未审核时为空。
     */
    @TableField("auditor_id")
    private Long auditorId;

    /**
     * 审核时间，由应用服务器生成本地日期时间，未审核时为空。
     */
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /**
     * 逻辑删除标记，0 表示未删除，1 表示已删除，沿用 MyBatis-Plus 逻辑删除机制。
     */
    @TableLogic
    @TableField("is_delete")
    private Integer isDelete;

    /**
     * 创建时间，由数据库在创建数据行时生成，创建后保持不变。
     */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    /**
     * 用户最近一次主动编辑时间，初始为创建时间，由数据库默认值填充，用户或管理员编辑时由更新语句取数据库当前本地时间。
     */
    @TableField("edit_time")
    private LocalDateTime editTime;

    /**
     * MySQL 自动维护的最近变更时间，使用本地日期时间。逻辑删除等更新由 ON UPDATE 自动刷新。
     */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
