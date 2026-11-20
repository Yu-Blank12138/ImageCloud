package com.blank.imagecloud.models.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/** 上传头像后返回两个固定地址，供前端预览并在保存资料时提交。 */
@Value
@Schema(description = "头像上传结果，原图和压缩图均为公开固定地址")
public class UserAvatarUploadVo {
    /** 用于日常展示的压缩头像地址。 */
    @Schema(description = "压缩头像固定 HTTPS URL", format = "uri")
    String avatarUrl;

    /** 用于点击头像后加载的原图地址。 */
    @Schema(description = "头像原图固定 HTTPS URL", format = "uri")
    String avatarOriginalUrl;
}
