package com.blank.imagecloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blank.imagecloud.models.dto.image.UploaderThumbnailRow;
import com.blank.imagecloud.models.entity.Image;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** 图片主表的数据访问接口，由项目的 Mapper 扫描配置注册。 */
public interface ImageMapper extends BaseMapper<Image> {

    /**
     * 按上传者分组，各取最新审核通过的若干张图片压缩图对象键。
     * SQL 定义在 mapper/ImageMapper.xml，使用窗口函数在数据库内完成每组限量。
     *
     * @param uploaderIds 图片上传者 ID 集合，不能为空
     * @param limitPerUser 每个上传者最多返回的图片数量
     * @return 按上传者、创建时间倒序截取后的行结果，仅包含上传者和压缩图对象键
     */
    List<UploaderThumbnailRow> selectLatestApprovedThumbnails(@Param("uploaderIds") Collection<Long> uploaderIds,
                                                              @Param("limitPerUser") int limitPerUser);
}
