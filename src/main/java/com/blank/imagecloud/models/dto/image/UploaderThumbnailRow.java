package com.blank.imagecloud.models.dto.image;

import lombok.Getter;
import lombok.Setter;

/**
 * 按上传者分组查询图片压缩图时使用的行结果，避免直接复用数据库实体。
 */
@Getter
@Setter
public class UploaderThumbnailRow {

    /** 图片上传者用户 ID。 */
    private Long uploaderId;

    /** 压缩图在存储桶内的对象键。 */
    private String thumbnailObjectKey;
}
