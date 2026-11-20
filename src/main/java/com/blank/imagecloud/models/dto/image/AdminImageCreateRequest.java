package com.blank.imagecloud.models.dto.image;

import com.blank.imagecloud.constant.ImageConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 管理员添加图片时提交的元数据，通过 JSON 请求体提交。
 * 先调用上传接口获得 imageUrl，审核字段由服务端赋值。
 */
@Getter
@Setter
@Schema(description = "管理员添加图片请求")
public class AdminImageCreateRequest {


    /** 上传接口返回的原图 URL，后端据此读取 COS 图片属性。 */
    @URL(protocol = "https")
    @Size(max = 2048)
    @NotBlank
    @Schema(description = "上传接口返回的 imageUrl，须指向项目图片存储路径，必填", format = "uri")
    private String imageUrl;

    /** 所属空间 ID，省略或为空表示图片不属于任何空间。 */
    @Min(1)
    @Schema(description = "所属空间 ID，省略或为空表示图片不属于任何空间", example = "1", nullable = true)
    private Long spaceId;

    /** 图片展示名称，必填。 */
    @NotBlank
    @Size(max = ImageConstant.NAME_MAX_LENGTH)
    @Schema(description = "图片展示名称，最多 16 个字符", example = "海边日落")
    private String name;

    /** 图片说明，可省略。 */
    @Size(max = ImageConstant.DESCRIPTION_MAX_LENGTH)
    @Schema(description = "图片说明，最多 128 个字符", nullable = true)
    private String description;

    /** 图片大分类，可省略。 */
    @Size(max = ImageConstant.CATEGORY_MAX_LENGTH)
    @Schema(description = "图片大分类，最多 16 个字符", nullable = true)
    private String category;

    /**
     * 标签列表，使用 JSON 字符串数组提交。
     * 服务端会去除首尾空白、过滤空标签并去重。
     */
    @Schema(description = "标签列表，使用 JSON 字符串数组提交，单个标签最多 20 个字符", nullable = true)
    private List<@Size(max = ImageConstant.TAG_MAX_LENGTH) String> tags;

    /** VIP 查看限制，0 无限制，1 仅有效 VIP 可查看，默认 0。 */
    @Min(0)
    @Max(1)
    @Schema(description = "VIP 查看限制，0 无限制，1 仅有效 VIP 可查看", defaultValue = "0",
            allowableValues = {"0", "1"}, nullable = true)
    private Integer vipOnly;

    /**
     * 规整图片名称首尾空白，保留 null 交由校验处理。
     *
     * @param name 管理员提交的图片名称
     */
    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }

    /**
     * 规整图片说明首尾空白，空白字符串按未填写处理。
     *
     * @param description 管理员提交的图片说明
     */
    public void setDescription(String description) {
        this.description = description == null || description.isBlank() ? null : description.strip();
    }

    /**
     * 规整图片分类首尾空白，空白字符串按未分类处理。
     *
     * @param category 管理员提交的图片分类
     */
    public void setCategory(String category) {
        this.category = category == null || category.isBlank() ? null : category.strip();
    }
}
