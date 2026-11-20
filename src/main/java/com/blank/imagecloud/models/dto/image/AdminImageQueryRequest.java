package com.blank.imagecloud.models.dto.image;

import com.blank.imagecloud.constant.ImageConstant;
import com.blank.imagecloud.models.dto.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** 管理员图片列表的可选筛选条件，与分页参数一起通过 URL 查询参数提交。 */
@Getter
@Setter
@Schema(description = "管理员图片分页查询条件")
public class AdminImageQueryRequest extends PageRequest {

    /** 按图片主键精确匹配。 */
    @Positive
    @Schema(description = "可选图片 ID，精确匹配", example = "7")
    private Long id;

    /** 按所属空间 ID 精确匹配。 */
    @Positive
    @Schema(description = "可选空间 ID，精确匹配", example = "1")
    private Long spaceId;

    /** 按上传用户 ID 精确匹配。 */
    @Positive
    @Schema(description = "可选上传用户 ID，精确匹配", example = "42")
    private Long uploaderId;

    /** 审核状态按枚举值精确匹配。 */
    @Min(0)
    @Max(2)
    @Schema(description = "可选审核状态，精确匹配，0 未审核，1 通过，2 不通过",
            allowableValues = {"0", "1", "2"})
    private Integer auditStatus;

    /** VIP 查看限制精确匹配。 */
    @Min(0)
    @Max(1)
    @Schema(description = "可选 VIP 查看限制，精确匹配，0 无限制，1 仅有效 VIP",
            allowableValues = {"0", "1"})
    private Integer vipOnly;

    /** 按图片名称的字面片段进行包含匹配。 */
    @Size(max = ImageConstant.NAME_MAX_LENGTH)
    @Schema(description = "可选名称片段，最多 16 个字符，按字面包含匹配", example = "海边")
    private String name;

    /** 按大分类的字面片段进行包含匹配。 */
    @Size(max = ImageConstant.CATEGORY_MAX_LENGTH)
    @Schema(description = "可选分类片段，最多 16 个字符，按字面包含匹配", example = "风景")
    private String category;

    /** 创建时间的包含式下限，直接按本地日期时间查询。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "可选创建时间下限，包含边界，使用无时区的 ISO 8601 本地日期时间",
            type = "string", format = "local-date-time", example = "2026-11-01T00:00:00")
    private LocalDateTime createdAtStart;

    /** 创建时间的包含式上限，直接按本地日期时间查询。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "可选创建时间上限，包含边界，使用无时区的 ISO 8601 本地日期时间",
            type = "string", format = "local-date-time", example = "2026-11-30T23:59:59.999")
    private LocalDateTime createdAtEnd;

    /**
     * 规整名称搜索条件，空白表示未指定条件。
     *
     * @param name 前端提交的可选名称片段
     */
    public void setName(String name) {
        this.name = name == null || name.isBlank() ? null : name.strip();
    }

    /**
     * 规整分类搜索条件，空白表示未指定条件。
     *
     * @param category 前端提交的可选分类片段
     */
    public void setCategory(String category) {
        this.category = category == null || category.isBlank() ? null : category.strip();
    }
}
