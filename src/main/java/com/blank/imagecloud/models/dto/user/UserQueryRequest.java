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

import java.time.LocalDateTime;
import java.util.List;

/** 管理员用户列表的可选筛选条件，与分页参数一起通过 URL 查询参数提交。 */
@Getter
@Setter
@Schema(description = "管理员用户分页查询条件")
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
    @Schema(description = "可选昵称片段，最多 12 个字符，按字面包含匹配", example = "Alice")
    private String displayName;

    /** 平台角色多选，命中任一角色即符合条件。 */
    @Schema(description = "可选平台角色集合，可同时选择多个，命中任一角色即符合条件",
            allowableValues = {UserConstant.ROLE_USER, UserConstant.ROLE_VIP, UserConstant.ROLE_ADMIN})
    private List<@Pattern(regexp = UserConstant.ROLE_PATTERN) String> platformRoles;

    /** 账号状态按枚举值精确匹配。 */
    @Pattern(regexp = UserConstant.STATUS_PATTERN)
    @Schema(description = "可选账号状态，精确匹配",
            allowableValues = {UserConstant.STATUS_ACTIVE, UserConstant.STATUS_DISABLED})
    private String status;

    /** 创建时间的包含式下限，直接按本地日期时间查询，秒精度。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "可选创建时间下限，包含边界，使用无时区的 ISO 8601 本地日期时间，秒精度",
            type = "string", format = "local-date-time", example = "2026-11-01T00:00:00")
    private LocalDateTime createdAtStart;

    /** 创建时间的包含式上限，缺省时与下限配合表示查询起点到现在。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "可选创建时间上限，包含边界，使用无时区的 ISO 8601 本地日期时间，秒精度；"
            + "设置了下限而省略上限时，上限按当前时间处理",
            type = "string", format = "local-date-time", example = "2026-11-30T23:59:59")
    private LocalDateTime createdAtEnd;

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
     * 规整平台角色条件：去除空白和重复值，空集合表示未指定条件。
     *
     * @param platformRoles 前端提交的可选平台角色集合
     */
    public void setPlatformRoles(List<String> platformRoles) {
        this.platformRoles = platformRoles == null ? null : platformRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
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
