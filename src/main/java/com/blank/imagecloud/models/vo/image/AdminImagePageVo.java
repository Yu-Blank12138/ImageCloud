package com.blank.imagecloud.models.vo.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.List;

/** 管理员图片分页查询的结果。 */
@Value
@Schema(description = "管理员图片分页结果")
public class AdminImagePageVo {
    @Schema(description = "当前页的图片详情，按图片 ID 倒序")
    List<AdminImageVo> records;

    @Schema(description = "未逻辑删除的图片总数", example = "42")
    long total;

    @Schema(description = "当前页码，从 1 开始", example = "1")
    int page;

    @Schema(description = "每页条数", example = "20")
    int pageSize;
}
