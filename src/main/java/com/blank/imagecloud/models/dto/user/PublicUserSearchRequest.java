package com.blank.imagecloud.models.dto.user;

import com.blank.imagecloud.models.dto.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** 按昵称搜索用户的分页查询条件，通过 JSON 请求体提交。 */
@Getter
@Setter
@Schema(description = "按昵称搜索用户的分页查询条件")
public class PublicUserSearchRequest extends PageRequest {

    /** 昵称关键词，去除首尾空白后不能为空且最多 12 个字符。 */
    @Schema(description = "昵称关键词，按字面包含匹配", example = "玉阳", maxLength = 12)
    private String displayName;
}
