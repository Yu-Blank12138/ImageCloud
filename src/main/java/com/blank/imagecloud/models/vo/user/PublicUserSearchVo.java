package com.blank.imagecloud.models.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.List;

/** 按昵称搜索用户时返回的公开候选项。 */
@Value
@Schema(description = "公开用户搜索候选项")
public class PublicUserSearchVo {
    @Schema(description = "用户 ID", example = "42")
    Long id;

    @Schema(description = "登录账号，用于区分同名用户", example = "blank12138")
    String account;

    @Schema(description = "用户昵称", nullable = true)
    String displayName;

    @Schema(description = "可长期访问的固定头像地址，未提供头像时为空", nullable = true)
    String avatarUrl;

    @Schema(description = "该用户最新审核通过的图片压缩图地址，最多三个，没有可用图片时为空列表")
    List<String> previewImageUrls;
}
