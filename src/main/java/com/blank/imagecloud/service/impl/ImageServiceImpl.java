package com.blank.imagecloud.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.constant.ImageConstant;
import com.blank.imagecloud.exception.ErrorCode;
import com.blank.imagecloud.mapper.ImageMapper;
import com.blank.imagecloud.models.vo.image.AdminImageUploadVo;
import com.blank.imagecloud.models.dto.image.AdminImageAuditRequest;
import com.blank.imagecloud.models.dto.image.AdminImageCreateRequest;
import com.blank.imagecloud.models.dto.image.AdminImageQueryRequest;
import com.blank.imagecloud.models.dto.image.AdminImageUpdateRequest;
import com.blank.imagecloud.models.dto.image.UploaderThumbnailRow;
import com.blank.imagecloud.models.entity.Image;
import com.blank.imagecloud.models.vo.image.AdminImagePageVo;
import com.blank.imagecloud.models.vo.image.AdminImageVo;
import com.blank.imagecloud.models.vo.image.ImageUploadVo;
import com.blank.imagecloud.service.ImageService;
import com.blank.imagecloud.utils.CosUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 实现管理员图片上传、逻辑删除和管理视角详情查询。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {
    private final ImageMapper imageMapper;
    private final CosUtils cosUtils;

    /**
     * 独立上传文件，返回原图及压缩图的预览 URL。
     *
     * @param operatorId 已通过管理员鉴权的上传者 ID
     * @param file 待上传的图片文件
     * @return 原图和压缩图的短期预览地址
     */
    @Override
    public AdminImageUploadVo uploadByAdmin(Long operatorId, MultipartFile file) {
        ThrowUtils.throwIf(operatorId == null || operatorId <= 0, ErrorCode.INVALID_ACCESS_TOKEN);
        ImageUploadVo uploaded = cosUtils.uploadImage(file, ImageConstant.DEFAULT_STORAGE_PREFIX);
        AdminImageUploadVo response = new AdminImageUploadVo();
        response.setImageUrl(cosUtils.getObjectUrl(uploaded.getOriginalObjectKey()));
        response.setThumbnailUrl(cosUtils.getObjectUrl(uploaded.getThumbnailObjectKey()));
        return response;
    }

    /**
     * 根据已上传图片 URL 读取云端属性，并以单条插入保存图片信息。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param request 图片元数据及上传接口返回的 URL
     */
    @Override
    public void createByAdmin(Long operatorId, AdminImageCreateRequest request) {
        ThrowUtils.throwIf(operatorId == null || operatorId <= 0, ErrorCode.INVALID_ACCESS_TOKEN);
        ThrowUtils.throwIf(request == null || !StringUtils.hasText(request.getImageUrl()), ErrorCode.VALIDATION_FAILED);
        ImageUploadVo uploaded = cosUtils.readUploadedImage(request.getImageUrl());
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        Image image = new Image();
        BeanUtil.copyProperties(request, image, CopyOptions.create().ignoreNullValue().setIgnoreProperties("tags", "imageUrl"));
        BeanUtil.copyProperties(uploaded, image);
        image.setTags(normalizeTags(request.getTags()));
        image.setUploaderId(operatorId);
        image.setVipOnly(request.getVipOnly() == null ? ImageConstant.VIP_ONLY_DISABLED : request.getVipOnly());
        image.setLikeCount(0);
        image.setAuditStatus(ImageConstant.AUDIT_STATUS_APPROVED);
        image.setAuditorId(operatorId);
        image.setAuditTime(now);
        // 云端属性读取完成后，数据库只执行一条插入，使用自动提交。
        ThrowUtils.throwIf(imageMapper.insert(image) != 1, ErrorCode.INTERNAL_ERROR);
        log.info("Admin created image: operatorId={}, imageId={}", operatorId, image.getId());
    }

    /**
     * 通过 MyBatis-Plus 按 ID 逻辑删除图片，保留对象存储中的原图和压缩图。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param id 目标图片 ID
     */
    @Override
    public void deleteByAdmin(Long operatorId, Long id) {
        ThrowUtils.throwIf(operatorId == null || operatorId <= 0, ErrorCode.INVALID_ACCESS_TOKEN);
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        // 逻辑删除沿用全局配置，单条更新使用自动提交。
        int deleted = imageMapper.deleteById(id);
        ThrowUtils.throwIf(deleted == 0, ErrorCode.RESOURCE_NOT_FOUND);
        log.info("Admin deleted image: operatorId={}, imageId={}", operatorId, id);
    }

    /**
     * 读取图片记录，并补全详情中的访问地址。
     *
     * @param id 目标图片 ID
     * @return 管理员可见的图片详情
     */
    @Override
    public AdminImageVo getAdminImage(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        Image image = imageMapper.selectById(id);
        ThrowUtils.throwIf(image == null, ErrorCode.RESOURCE_NOT_FOUND);
        return toAdminImageVo(image);
    }

    /**
     * 按条件分页查询图片，条件同时生效，结果按图片 ID 倒序并补全访问地址。
     *
     * @param request 筛选条件与分页参数
     * @return 包含图片详情列表、总数和分页参数的结果
     */
    @Override
    public AdminImagePageVo pageAdminImages(AdminImageQueryRequest request) {
        ThrowUtils.throwIf(request.getCreatedAtStart() != null && request.getCreatedAtEnd() != null
                        && request.getCreatedAtStart().isAfter(request.getCreatedAtEnd()),
                ErrorCode.VALIDATION_FAILED, "创建时间下限不能晚于上限");
        // name 与 category 为字面包含匹配，显式转义下划线，搜索值通过占位参数绑定。
        var query = Wrappers.<Image>lambdaQuery()
                .eq(request.getId() != null, Image::getId, request.getId())
                .eq(request.getSpaceId() != null, Image::getSpaceId, request.getSpaceId())
                .eq(request.getUploaderId() != null, Image::getUploaderId, request.getUploaderId())
                .eq(request.getAuditStatus() != null, Image::getAuditStatus, request.getAuditStatus())
                .eq(request.getVipOnly() != null, Image::getVipOnly, request.getVipOnly())
                .apply(request.getName() != null, "name LIKE {0} ESCAPE '!'",
                        containsPattern(request.getName()))
                .apply(request.getCategory() != null, "category LIKE {0} ESCAPE '!'",
                        containsPattern(request.getCategory()))
                .ge(request.getCreatedAtStart() != null, Image::getCreatedAt,
                        request.getCreatedAtStart())
                .le(request.getCreatedAtEnd() != null, Image::getCreatedAt,
                        request.getCreatedAtEnd())
                .orderByDesc(Image::getId);
        Page<Image> result = imageMapper.selectPage(new Page<>(request.getPage(), request.getPageSize()), query);
        return new AdminImagePageVo(result.getRecords().stream().map(this::toAdminImageVo).toList(),
                result.getTotal(), request.getPage(), request.getPageSize());
    }

    /**
     * 局部更新元数据，提供上传 URL 时同时替换图片，管理员编辑刷新 edit_time。
     *
     * @param operatorId 已通过管理员鉴权的当前用户 ID
     * @param request 携带目标图片 ID 与可更新字段，省略或 null 表示保留
     */
    @Override
    public void updateByAdmin(Long operatorId, AdminImageUpdateRequest request) {
        ThrowUtils.throwIf(operatorId == null || operatorId <= 0, ErrorCode.INVALID_ACCESS_TOKEN);
        Long id = request == null ? null : request.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        // 空字符串清空说明、分类，以及空数组清空标签，均属于有效更新；目标 ID 不计入可更新字段。
        ThrowUtils.throwIf(BeanUtil.isEmpty(request, "id"),
                ErrorCode.VALIDATION_FAILED, "至少提交一个可更新字段");
        Image update = new Image();
        update.setId(id);
        BeanUtil.copyProperties(request, update,
                CopyOptions.create().ignoreNullValue().setIgnoreProperties("tags", "imageUrl"));
        if (request.getTags() != null) {
            update.setTags(normalizeTags(request.getTags()));
        }
        if (request.getImageUrl() != null) {
            ThrowUtils.throwIf(!StringUtils.hasText(request.getImageUrl()), ErrorCode.VALIDATION_FAILED);
            BeanUtil.copyProperties(cosUtils.readUploadedImage(request.getImageUrl()), update);
        }
        // 同一条 SQL 保存数据并用数据库当前本地时间刷新编辑时间，避免应用与数据库时钟差异。
        ThrowUtils.throwIf(imageMapper.update(update, Wrappers.<Image>lambdaUpdate()
                .eq(Image::getId, id).setSql("edit_time = CURRENT_TIMESTAMP(3)")) != 1,
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 审核图片，单条更新写入审核结论、审核人、审核时间和审核信息，允许对已审核图片改判。
     *
     * @param auditorId 已通过管理员鉴权的审核人 ID
     * @param request 目标图片 ID、审核结论和审核信息
     */
    @Override
    public void auditByAdmin(Long auditorId, AdminImageAuditRequest request) {
        ThrowUtils.throwIf(auditorId == null || auditorId <= 0, ErrorCode.INVALID_ACCESS_TOKEN);
        ThrowUtils.throwIf(request == null, ErrorCode.VALIDATION_FAILED);
        Long id = request.getId();
        Integer auditStatus = request.getAuditStatus();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.VALIDATION_FAILED);
        ThrowUtils.throwIf(auditStatus == null
                        || (auditStatus != ImageConstant.AUDIT_STATUS_APPROVED
                        && auditStatus != ImageConstant.AUDIT_STATUS_REJECTED),
                ErrorCode.VALIDATION_FAILED, "审核结论只能是通过或不通过");
        String auditMessage = StringUtils.hasText(request.getAuditMessage())
                ? request.getAuditMessage().strip() : null;
        ThrowUtils.throwIf(auditStatus == ImageConstant.AUDIT_STATUS_REJECTED && auditMessage == null,
                ErrorCode.VALIDATION_FAILED, "审核不通过时必须填写审核信息");
        // 通过时清空历史审核信息，避免驳回原因残留；逻辑删除条件由 MyBatis-Plus 自动追加。
        LocalDateTime auditTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        int updated = imageMapper.update(Wrappers.<Image>lambdaUpdate()
                .eq(Image::getId, id)
                .set(Image::getAuditStatus, auditStatus)
                .set(Image::getAuditMessage,
                        auditStatus == ImageConstant.AUDIT_STATUS_APPROVED ? null : auditMessage)
                .set(Image::getAuditorId, auditorId)
                .set(Image::getAuditTime, auditTime));
        ThrowUtils.throwIf(updated != 1, ErrorCode.RESOURCE_NOT_FOUND);
        log.info("Admin audited image: auditorId={}, imageId={}, auditStatus={}", auditorId, id, auditStatus);
    }

    /**
     * 批量读取多个用户各自最新审核通过的图片压缩图地址。
     *
     * @param uploaderIds 图片上传者 ID 集合
     * @param limitPerUser 每个用户最多返回的图片数量
     * @return 用户 ID 到按创建时间倒序的长期压缩图地址的映射
     */
    @Override
    public Map<Long, List<String>> listLatestApprovedThumbnailUrls(Collection<Long> uploaderIds, int limitPerUser) {
        ThrowUtils.throwIf(limitPerUser <= 0, ErrorCode.VALIDATION_FAILED);
        if (uploaderIds == null || uploaderIds.isEmpty()) {
            return Map.of();
        }
        // 数据库按用户分组各取最新若干张，Java 侧只负责拼接长期地址并按用户归类。
        List<UploaderThumbnailRow> rows = imageMapper.selectLatestApprovedThumbnails(uploaderIds, limitPerUser);
        Map<Long, List<String>> result = new HashMap<>();
        for (UploaderThumbnailRow row : rows) {
            result.computeIfAbsent(row.getUploaderId(), key -> new ArrayList<>())
                    .add(cosUtils.getObjectUrl(row.getThumbnailObjectKey()));
        }
        return result;
    }

    /**
     * 将对象键补全为前端可直接访问的长期地址，并组装管理视角详情。
     *
     * @param image 已读出的图片实体
     * @return 包含完整图片地址和审核信息的详情
     */
    private AdminImageVo toAdminImageVo(Image image) {
        AdminImageVo vo = new AdminImageVo();
        // 同名字段及本地日期时间直接拷贝，图片地址按对象键拼接长期访问地址。
        BeanUtil.copyProperties(image, vo, CopyOptions.create().setIgnoreNullValue(true));
        vo.setOriginalUrl(cosUtils.getObjectUrl(image.getOriginalObjectKey()));
        vo.setThumbnailUrl(StringUtils.hasText(image.getThumbnailObjectKey())
                ? cosUtils.getObjectUrl(image.getThumbnailObjectKey())
                : null);
        return vo;
    }

    /**
     * 去除标签首尾空白、过滤空标签并去重，无标签时返回空列表。
     *
     * @param tags 管理员提交的标签列表，可为空
     * @return 可直接写入 JSON 列的标签列表
     */
    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 将名称或分类片段转换为字面包含匹配参数，转义 SQL LIKE 的特殊字符。
     *
     * @param value 已规整的搜索片段，为 null 时表示未指定条件
     * @return 包含前后通配符的转义参数，未指定条件时返回 null
     */
    private static String containsPattern(String value) {
        return value == null ? null : "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
