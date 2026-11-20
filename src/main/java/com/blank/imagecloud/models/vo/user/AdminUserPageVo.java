package com.blank.imagecloud.models.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.List;

/** 管理员用户分页查询的结果。 */
@Value
@Schema(description = "管理员用户分页结果")
public class AdminUserPageVo {
    @Schema(description = "当前页的用户资料，按用户 ID 倒序")
    List<AdminUserVo> records;

    @Schema(description = "未逻辑删除的用户总数", example = "42")
    long total;

    @Schema(description = "当前页码，从 1 开始", example = "1")
    int page;

    @Schema(description = "每页条数", example = "20")
    int pageSize;
}
