package com.blank.imagecloud.models.vo.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员视角的图片详情，包含图片元数据、审核信息和可直接访问的图片地址。
 * 图片地址为按对象键拼接的长期访问地址，存储桶为公有读。
 */
@Getter
@Setter
@Schema(description = "管理员图片详情")
public class AdminImageVo {

    @Schema(description = "图片 ID", example = "7")
    Long id;

    @Schema(description = "所属空间 ID，为空表示图片不属于任何空间", nullable = true)
    Long spaceId;

    @Schema(description = "上传用户 ID", example = "42")
    Long uploaderId;

    @Schema(description = "图片展示名称", example = "海边日落")
    String name;

    @Schema(description = "图片说明", nullable = true)
    String description;

    @Schema(description = "图片大分类", nullable = true)
    String category;

    @Schema(description = "标签列表，无标签时为空数组")
    List<String> tags;

    @Schema(description = "首次上传的原始文件名")
    String originalFilename;

    @Schema(description = "原图长期访问地址，可直接访问原图")
    String originalUrl;

    @Schema(description = "展示用压缩图长期访问地址，可直接用于列表展示", nullable = true)
    String thumbnailUrl;

    @Schema(description = "原图实际格式，小写", example = "jpeg")
    String format;

    @Schema(description = "原图大小，单位字节")
    Long fileSize;

    @Schema(description = "原图宽度，单位像素")
    Integer width;

    @Schema(description = "原图高度，单位像素")
    Integer height;

    @Schema(description = "VIP 查看限制，0 无限制，1 仅有效 VIP 可查看")
    Integer vipOnly;

    @Schema(description = "点赞数落库快照")
    Integer likeCount;

    @Schema(description = "审核状态，0 未审核，1 通过，2 不通过", allowableValues = {"0", "1", "2"})
    Integer auditStatus;

    @Schema(description = "审核信息，未审核时为空", nullable = true)
    String auditMessage;

    @Schema(description = "审核人用户 ID，未审核时为空", nullable = true)
    Long auditorId;

    @Schema(description = "审核时间，使用本地日期时间，未审核时为空", type = "string", format = "local-date-time", nullable = true)
    LocalDateTime auditTime;

    @Schema(description = "创建时间，使用本地日期时间", type = "string", format = "local-date-time")
    LocalDateTime createdAt;

    @Schema(description = "用户最近一次主动编辑时间，使用本地日期时间，初始为创建时间", type = "string", format = "local-date-time", nullable = true)
    LocalDateTime editTime;

    @Schema(description = "记录最近一次变更时间，使用本地日期时间", type = "string", format = "local-date-time")
    LocalDateTime updateTime;
}
