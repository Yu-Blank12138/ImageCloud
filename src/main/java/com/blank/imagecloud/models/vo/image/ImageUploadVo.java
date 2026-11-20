package com.blank.imagecloud.models.vo.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 图片上传结果，封装原图、展示用压缩图对象引用及数据万象识别的原图信息。
 */
@Getter
@Setter
@Schema(description = "图片上传结果")
public class ImageUploadVo {

    /**
     * 原图在私有 COS 桶内的完整对象键。
     */
    @Schema(description = "原图对象键，供详情展示使用", example = "images/20261118_uuid.jpg")
    private String originalObjectKey;

    /**
     * 数据万象持久化生成的压缩图对象键，供前端列表展示使用。
     */
    @Schema(description = "展示用压缩图对象键，WebP 格式", example = "images/20261118_uuid_compressed.webp")
    private String thumbnailObjectKey;

    /**
     * 上传文件的原始名称，由上传请求提供。
     */
    @Schema(description = "上传文件原始名称", nullable = true)
    private String originalFilename;

    /**
     * 上传原图大小，单位字节。
     */
    @Schema(description = "原图大小，单位字节")
    private Long fileSize;

    /**
     * 数据万象识别的图片格式，统一转换为小写。
     */
    @Schema(description = "实际图片格式，小写", example = "jpeg")
    private String format;

    /**
     * 数据万象识别的原图宽度，单位像素。
     */
    @Schema(description = "图片宽度，单位像素")
    private Integer width;

    /**
     * 数据万象识别的原图高度，单位像素。
     */
    @Schema(description = "图片高度，单位像素")
    private Integer height;

    /**
     * 数据万象返回的图片质量值。
     */
    @Schema(description = "数据万象识别的图片质量")
    private Integer quality;

    /**
     * 数据万象返回的图片主色调，格式为 0xRRGGBB。
     */
    @Schema(description = "图片主色调", example = "0xa18454", nullable = true)
    private String ave;

    /**
     * 数据万象返回的图片方向信息，保留云端数值。
     */
    @Schema(description = "图片方向信息，保留数据万象原值")
    private Integer orientation;

    /**
     * 数据万象返回的帧数，静态图片为 1，动图为对应帧数。
     */
    @Schema(description = "图片帧数，静态图片为 1")
    private Integer frameCount;
}
