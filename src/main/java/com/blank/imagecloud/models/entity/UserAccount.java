package com.blank.imagecloud.models.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("user_account")
public class UserAccount {
    /** 用户主键，由应用侧雪花算法生成。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一登录账号，区分大小写。 */
    @TableField("account")
    private String account;

    /** 带算法标识的密码哈希，用于登录时校验密码。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 用户展示昵称，未设置时为空。 */
    @TableField("display_name")
    private String displayName;

    /** 压缩头像的公开固定 URL，未上传时为空。 */
    @TableField("avatar_url")
    private String avatarUrl;

    /** 头像原图的公开固定 URL，未设置原图时为空。 */
    @TableField("avatar_original_url")
    private String avatarOriginalUrl;

    /** 用户性别，0 未设置，1 男，2 女，可为空。 */
    @TableField("gender")
    private Integer gender;

    /** 用户填写的自我介绍，最多 128 个字符。 */
    @TableField("bio")
    private String bio;

    /** 用户联系邮箱，允许在公开资料中展示。 */
    @TableField("email")
    private String email;

    /** 用户手机号，仅本人和管理员可查看。 */
    @TableField("phone_number")
    private String phoneNumber;

    /** 出生年月，采用 YYYY-MM 格式，可为空。 */
    @TableField("birth_month")
    private String birthMonth;

    /** 平台角色。USER 为普通用户，VIP 为会员，ADMIN 为平台管理员。 */
    @TableField("platform_role")
    private String platformRole;

    /** 账号状态。ACTIVE 为正常，DISABLED 为停用。 */
    @TableField("status")
    private String status;

    /** VIP 权益的到期日期，精确到日，到期当天仍然有效。VIP 角色必须填写。 */
    @TableField("vip_expires_at")
    private LocalDate vipExpiresAt;

    /** 逻辑删除标记，沿用全局配置。0 表示未删除，1 表示已删除。 */
    @TableLogic
    @TableField("is_delete")
    private Integer isDelete;

    /** 账号创建时间，由数据库在创建数据行时生成，创建后保持不变。 */
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    /** 用户或管理员最近一次编辑资料的时间，初始由数据库取创建时间，之后由用户更新语句取数据库当前本地时间。 */
    @TableField("edit_time")
    private LocalDateTime editTime;

    /** MySQL 自动维护的记录更新时间，使用本地日期时间。由数据库在数据更新时刷新。 */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
