package com.blank.imagecloud.models.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/** 对外公开的用户基本资料。 */
@Value
@Schema(description = "公开用户资料")
public class PublicUserVo {
    @Schema(description = "用户 ID", example = "42")
    Long id;

    @Schema(description = "用户昵称", nullable = true)
    String displayName;

    @Schema(description = "可访问的短期头像地址，未提供头像时为空", nullable = true)
    String avatarUrl;
}
