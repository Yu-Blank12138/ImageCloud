package com.blank.imagecloud.service;

import com.blank.imagecloud.models.dto.image.AdminImageCreateRequest;
import com.blank.imagecloud.models.vo.image.AdminImageUploadVo;
import com.blank.imagecloud.models.dto.image.AdminImageAuditRequest;
import com.blank.imagecloud.models.dto.image.AdminImageQueryRequest;
import com.blank.imagecloud.models.dto.image.AdminImageUpdateRequest;
import com.blank.imagecloud.models.vo.image.AdminImagePageVo;
import com.blank.imagecloud.models.vo.image.AdminImageVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 定义管理员创建、逻辑删除和查询图片的业务操作。 */
public interface ImageService {

    /**
     * 独立上传图片，返回后续提交所需的 URL。
     *
     * @param operatorId 当前管理员 ID
     * @param file 图片文件
     * @return 原图与压缩图的预览 URL
     */
    AdminImageUploadVo uploadByAdmin(Long operatorId, MultipartFile file);

    /**
     * 使用独立上传得到的 URL 保存图片，审核状态直接置为通过。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID，作为上传者和审核人
     * @param request 图片元数据，空间 ID 可为空
     */
    void createByAdmin(Long operatorId, AdminImageCreateRequest request);

    /**
     * 按 ID 逻辑删除图片，不删除对象存储中的原图和压缩图。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param id 目标图片 ID
     */
    void deleteByAdmin(Long operatorId, Long id);

    /**
     * 读取管理员可见的图片详情，并将对象键补全为可访问地址。
     *
     * @param id 目标图片 ID
     * @return 包含图片元数据、审核信息和短期访问地址的详情
     */
    AdminImageVo getAdminImage(Long id);

    /**
     * 按条件分页查询管理员可见的图片详情，结果按图片 ID 倒序并补全访问地址。
     *
     * @param request 筛选条件与分页参数
     * @return 包含图片详情列表、总数和分页参数的结果
     */
    AdminImagePageVo pageAdminImages(AdminImageQueryRequest request);

    /**
     * 批量读取多个用户各自最新审核通过的图片压缩图地址，供用户搜索预览使用。
     *
     * @param uploaderIds 图片上传者 ID 集合，为空时直接返回空映射
     * @param limitPerUser 每个用户最多返回的图片数量，必须大于零
     * @return 用户 ID 到按创建时间倒序的长期压缩图地址的映射，没有可用图片的用户不在映射中
     */
    Map<Long, List<String>> listLatestApprovedThumbnailUrls(Collection<Long> uploaderIds, int limitPerUser);

    /**
     * 局部更新图片元数据，可选地通过上传 URL 替换图片。
     * 省略的字段保持原值；提交 imageUrl 时从云端读取图片属性。
     * 目标图片 ID 由请求体 DTO 携带，修改类接口不在路径上单独传 ID。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param request 本次提交的目标 ID 与可更新字段
     */
    void updateByAdmin(Long operatorId, AdminImageUpdateRequest request);

    /**
     * 审核图片，写入审核结论、审核人、审核时间和审核信息，允许对已审核图片改判。
     * 审核人取自登录态；不通过时必须提交审核信息，通过时清空历史审核信息。
     *
     * @param auditorId 已通过管理员鉴权的审核人 ID
     * @param request 目标图片 ID、审核结论和审核信息
     */
    void auditByAdmin(Long auditorId, AdminImageAuditRequest request);
}
