package com.blank.imagecloud.models.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.List;

/** 按昵称搜索用户的分页结果。 */
@Value
@Schema(description = "按昵称搜索用户的分页结果")
public class PublicUserSearchPageVo {
    @Schema(description = "当前页候选，昵称完全一致的排最前，其次是有可用图片的用户")
    List<PublicUserSearchVo> records;

    @Schema(description = "昵称包含关键词的正常账号总数", example = "12")
    long total;

    @Schema(description = "当前页码，从 1 开始", example = "1")
    int page;

    @Schema(description = "每页条数", example = "9")
    int pageSize;
}
