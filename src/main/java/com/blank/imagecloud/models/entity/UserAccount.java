package com.blank.imagecloud.models.vo.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("user_account")
public class UserAccount {
    /** 用户主键，由 MySQL 自增生成。 */
    @TableId(value = "id", type = IdType.AUTO)
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

    /** 头像的 COS 原始对象 URL，未上传时为空。 */
    @TableField("avatar_url")
    private String avatarUrl;

    /** 平台角色。USER 为普通用户，VIP 为会员，ADMIN 为平台管理员。 */
    @TableField("platform_role")
    private String platformRole;

    /** 账号状态。ACTIVE 为正常，DISABLED 为停用。 */
    @TableField("status")
    private String status;

    /** VIP 权益的到期时间，使用 UTC。VIP 角色必须填写。 */
    @TableField("vip_expires_at")
    private LocalDateTime vipExpiresAt;

    /** 逻辑删除标记，沿用全局配置。0 表示未删除，1 表示已删除。 */
    @TableLogic
    @TableField("is_delete")
    private Integer isDelete;

    /** 账号创建时间，由应用服务器生成，使用 UTC，创建后保持不变。 */
    @TableField(value = "created_at", updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    /** 用户最近一次自行修改账号信息的时间，初始为创建时间，由服务层写入，使用 UTC。 */
    @TableField("edit_time")
    private LocalDateTime editTime;

    /** MySQL 自动维护的记录更新时间，使用 UTC。对象可暂存应用生成的返回时间，允许与数据库略有差异。 */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
