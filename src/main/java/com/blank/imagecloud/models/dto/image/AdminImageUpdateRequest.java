package com.blank.imagecloud.models.dto.image;

import com.blank.imagecloud.constant.ImageConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 管理员局部更新图片的元数据，通过 JSON 请求体提交。
 * 省略或为 null 的字段保持原值；提交 imageUrl 时使用已上传图片替换原图。
 */
@Getter
@Setter
@Schema(description = "管理员更新图片请求，省略或为 null 的字段保持原值")
public class AdminImageUpdateRequest {

    /** 目标图片 ID，修改类请求的 ID 统一放在请求体中。 */
    @NotNull
    @Positive
    @Schema(description = "目标图片 ID", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    /** 上传接口返回的原图 URL，后端据此读取 COS 图片属性。 */
    @URL(protocol = "https")
    @Size(max = 2048)
    @Schema(description = "上传接口返回的 imageUrl，须指向项目图片存储路径，省略时保持原图片", format = "uri")
    private String imageUrl;

    /** 新的图片名称，空白表示保持原值。 */
    @Size(max = ImageConstant.NAME_MAX_LENGTH)
    @Schema(description = "新的图片名称，省略或空白时保持原值，最多 16 个字符", example = "海边日落", nullable = true)
    private String name;

    /** 新的图片说明，空字符串表示清空。 */
    @Size(max = ImageConstant.DESCRIPTION_MAX_LENGTH)
    @Schema(description = "新的图片说明，省略时保持原值，传空字符串表示清空，最多 128 个字符", nullable = true)
    private String description;

    /** 新的大分类，空字符串表示清空。 */
    @Size(max = ImageConstant.CATEGORY_MAX_LENGTH)
    @Schema(description = "新的大分类，省略时保持原值，传空字符串表示清空，最多 16 个字符", nullable = true)
    private String category;

    /**
     * 新的标签列表，省略时保持原值，提交后服务端会去除首尾空白、过滤空标签并去重，可得到清空效果。
     */
    @Schema(description = "新的标签列表，使用 JSON 字符串数组提交，省略时保持原值，单个标签最多 20 个字符",
            nullable = true)
    private List<@Size(max = ImageConstant.TAG_MAX_LENGTH) String> tags;

    /** 新的 VIP 查看限制，省略时保持原值。 */
    @Min(0)
    @Max(1)
    @Schema(description = "新的 VIP 查看限制，省略时保持原值，0 无限制，1 仅有效 VIP",
            allowableValues = {"0", "1"}, nullable = true)
    private Integer vipOnly;

    /** 新的所属空间 ID，省略时保持原值。 */
    @Min(1)
    @Schema(description = "新的所属空间 ID，省略时保持原值", example = "1", nullable = true)
    private Long spaceId;

    /**
     * 规整图片名称，空白表示保持原值。
     *
     * @param name 前端提交的可选图片名称
     */
    public void setName(String name) {
        this.name = name == null || name.isBlank() ? null : name.strip();
    }

    /**
     * 规整图片说明，保留空字符串用于清空语义。
     *
     * @param description 前端提交的可选图片说明
     */
    public void setDescription(String description) {
        this.description = description == null ? null : description.strip();
    }

    /**
     * 规整大分类，保留空字符串用于清空语义。
     *
     * @param category 前端提交的可选大分类
     */
    public void setCategory(String category) {
        this.category = category == null ? null : category.strip();
    }
}
