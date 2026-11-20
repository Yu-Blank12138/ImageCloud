package com.blank.imagecloud.models.vo.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** 管理员独立上传后的原图与压缩图预览地址。 */
@Getter
@Setter
@Schema(description = "管理员图片上传结果")
public class AdminImageUploadVo {
    @Schema(description = "原图短期预览地址，新增或替换图片时原样作为 imageUrl 提交，预览有效期一小时")
    private String imageUrl;

    @Schema(description = "压缩图短期预览地址，有效期一小时")
    private String thumbnailUrl;

}
