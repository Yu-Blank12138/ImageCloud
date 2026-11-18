package com.blank.imagecloud.models.dto.user;

import com.blank.imagecloud.constant.UserConstant;
import com.blank.imagecloud.models.dto.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/** 管理员用户列表的可选筛选条件，与分页参数一起通过 URL 查询参数提交。 */
@Getter
@Setter
@Schema(description = "管理员用户分页查询条件，省略或空白的条件不参与筛选")
public class UserQueryRequest extends PageRequest {
    /** 按用户主键精确匹配。 */
    @Positive
    @Schema(description = "可选用户 ID，精确匹配", example = "42")
    private Long id;

    /** 区分大小写的账号片段，下划线作为普通字符匹配。 */
    @Pattern(regexp = UserConstant.ACCOUNT_SEARCH_PATTERN)
    @Schema(description = "可选账号片段，1–32 位英文字母、数字或下划线，区分大小写并按字面包含匹配",
            example = "Alice_")
    private String account;

    /** 按展示昵称的字面片段进行包含匹配。 */
    @Size(max = UserConstant.DISPLAY_NAME_MAX_LENGTH)
    @Schema(description = "可选昵称片段，最多 64 个字符，按字面包含匹配", example = "Alice")
    private String displayName;

    /** 平台角色按枚举值精确匹配。 */
    @Pattern(regexp = UserConstant.ROLE_PATTERN)
    @Schema(description = "可选平台角色，精确匹配",
            allowableValues = {UserConstant.ROLE_USER, UserConstant.ROLE_VIP, UserConstant.ROLE_ADMIN})
    private String platformRole;

    /** 账号状态按枚举值精确匹配。 */
    @Pattern(regexp = UserConstant.STATUS_PATTERN)
    @Schema(description = "可选账号状态，精确匹配",
            allowableValues = {UserConstant.STATUS_ACTIVE, UserConstant.STATUS_DISABLED})
    private String status;

    /** 创建时间的包含式下限，带时区的输入转换为 UTC 后查询。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "可选创建时间下限，包含边界，使用带时区的 ISO 8601 时间",
            format = "date-time", example = "2026-11-01T00:00:00Z")
    private Instant createdAtStart;

    /** 创建时间的包含式上限，带时区的输入转换为 UTC 后查询。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "可选创建时间上限，包含边界，使用带时区的 ISO 8601 时间",
            format = "date-time", example = "2026-11-30T23:59:59.999Z")
    private Instant createdAtEnd;

    /**
     * 规整账号搜索条件，保留大小写，空白表示未指定条件。
     *
     * @param account 前端提交的可选账号片段
     */
    public void setAccount(String account) {
        this.account = account == null || account.isBlank() ? null : account.strip();
    }

    /**
     * 规整昵称搜索条件，空白表示未指定条件。
     *
     * @param displayName 前端提交的可选昵称片段
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? null : displayName.strip();
    }

    /**
     * 规整平台角色条件，空白表示未指定条件。
     *
     * @param platformRole 前端提交的可选平台角色
     */
    public void setPlatformRole(String platformRole) {
        this.platformRole = platformRole == null || platformRole.isBlank() ? null : platformRole.strip();
    }

    /**
     * 规整账号状态条件，空白表示未指定条件。
     *
     * @param status 前端提交的可选账号状态
     */
    public void setStatus(String status) {
        this.status = status == null || status.isBlank() ? null : status.strip();
    }
}
