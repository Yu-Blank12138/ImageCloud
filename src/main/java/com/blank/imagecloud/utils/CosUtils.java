package com.blank.imagecloud.utils;

import cn.hutool.core.bean.BeanUtil;
import com.blank.imagecloud.common.ThrowUtils;
import com.blank.imagecloud.config.CosConfig;
import com.blank.imagecloud.models.vo.image.ImageUploadVo;
import com.blank.imagecloud.exception.ErrorCode;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 复用 COS 客户端执行图片上传和流式下载，并封装数据万象返回的图片基础信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CosUtils {

    /**
     * 本项目图片上传大小上限为 20MB。
     */
    private static final long MAX_UPLOAD_SIZE = 20L * 1024 * 1024;

    /**
     * 允许上传的图片文件后缀，统一转为小写后校验。
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff");

    /**
     * 展示图转换为 WebP，质量取原图质量和 80 的较小值。
     */
    private static final String COMPRESSION_RULE = "imageMogr2/format/webp/quality/80";

    private final COSClient cosClient;
    private final CosConfig cosConfig;
    private final ObjectMapper objectMapper;

    /**
     * 上传原图并由数据万象同步生成展示用压缩图，取得云端识别的原图信息。
     * 校验文件后缀，在指定前缀下生成 yyyyMMdd_uuid.后缀 的对象键，日期使用服务器本地日期。
     * 调用方负责上传权限，图片格式、宽高等信息以数据万象返回结果为准。
     * 云端写入成功但原图信息或压缩结果缺失、无效时抛出业务异常。
     *
     * @param file 待上传的非空文件，大小不超过 20MB
     * @param uploadPrefix 存储前缀，例如 avatars/、images/ 或 spaces/1/，可省略末尾斜杠
     * @return 原图和压缩图对象键、原图大小及数据万象识别的原图基础信息
     */
    public ImageUploadVo uploadImage(MultipartFile file, String uploadPrefix) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.VALIDATION_FAILED, "上传文件不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(uploadPrefix), ErrorCode.VALIDATION_FAILED, "存储前缀不能为空");
        ThrowUtils.throwIf(file.getSize() > MAX_UPLOAD_SIZE, ErrorCode.PAYLOAD_TOO_LARGE, "上传图片大小不能超过 20MB");
        ThrowUtils.throwIf(!StringUtils.hasText(cosConfig.getBucket()), ErrorCode.INTERNAL_ERROR, "COS 存储桶未配置");

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        extension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(!IMAGE_EXTENSIONS.contains(extension), ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "仅支持 jpg、jpeg、png、webp、gif、bmp、tif、tiff 图片后缀");

        ThrowUtils.throwIf(file.getOriginalFilename().length() > 255,
                ErrorCode.VALIDATION_FAILED, "原始文件名不能超过 255 个字符");
        String filename = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_" + UUID.randomUUID();
        String prefix = uploadPrefix.endsWith("/") ? uploadPrefix : uploadPrefix + "/";
        String originalObjectKey = prefix + filename + "." + extension;
        String thumbnailObjectKey = prefix + filename + "_compressed.webp";

        PicOperations operations = new PicOperations();
        operations.setIsPicInfo(1);
        PicOperations.Rule compression = new PicOperations.Rule();
        compression.setBucket(cosConfig.getBucket());
        // 以 / 开头表示桶内绝对路径，确保空间等多级前缀下的结果位置一致。
        compression.setFileId("/" + thumbnailObjectKey);
        compression.setRule(COMPRESSION_RULE);
        operations.setRules(List.of(compression));
        PutObjectResult uploadResult = uploadFile(file, originalObjectKey, operations);
        CIUploadResult ciResult = uploadResult.getCiUploadResult();
        ThrowUtils.throwIf(ciResult == null || ciResult.getOriginalInfo() == null
                        || ciResult.getOriginalInfo().getImageInfo() == null,
                ErrorCode.SERVICE_UNAVAILABLE, "数据万象未返回图片信息");

        ImageInfo imageInfo = ciResult.getOriginalInfo().getImageInfo();
        ThrowUtils.throwIf(imageInfo.getWidth() <= 0 || imageInfo.getHeight() <= 0
                        || !StringUtils.hasText(imageInfo.getFormat()),
                ErrorCode.SERVICE_UNAVAILABLE, "数据万象返回的图片信息无效");

        ThrowUtils.throwIf(ciResult.getProcessResults() == null
                        || ciResult.getProcessResults().getObjectList() == null
                        || ciResult.getProcessResults().getObjectList().isEmpty(),
                ErrorCode.SERVICE_UNAVAILABLE, "数据万象未返回压缩图");
        CIObject compressed = ciResult.getProcessResults().getObjectList().get(0);
        ThrowUtils.throwIf(compressed == null || !thumbnailObjectKey.equals(compressed.getKey())
                        || !"webp".equalsIgnoreCase(compressed.getFormat())
                        || compressed.getSize() == null || compressed.getSize() <= 0,
                ErrorCode.SERVICE_UNAVAILABLE, "数据万象返回的压缩图无效");

        ImageUploadVo result = BeanUtil.copyProperties(imageInfo, ImageUploadVo.class);
        result.setOriginalObjectKey(originalObjectKey);
        result.setThumbnailObjectKey(compressed.getKey());
        result.setOriginalFilename(file.getOriginalFilename());
        result.setFileSize(file.getSize());
        result.setFormat(imageInfo.getFormat().toLowerCase(Locale.ROOT));
        return result;
    }

    /**
     * 执行底层流式上传并关闭输入流，相同对象键会覆盖已有对象。
     *
     * @param file 已通过基础校验的上传文件
     * @param objectKey 图片上传业务生成的完整对象键
     * @param operations 上传时提交给数据万象的图片处理参数
     * @return SDK 上传结果，供图片上传业务读取数据万象信息
     */
    private PutObjectResult uploadFile(MultipartFile file, String objectKey, PicOperations operations) {
        ObjectMetadata metadata = new ObjectMetadata();
        // 已知长度可避免 SDK 为计算长度而将整个文件缓冲到内存。
        metadata.setContentLength(file.getSize());
        // 使用 ASCII 编码保存原文件名，后续提交 URL 时可从对象元数据恢复中文文件名。
        metadata.addUserMetadata("original-filename", Base64.getUrlEncoder().encodeToString(
                file.getOriginalFilename().getBytes(StandardCharsets.UTF_8)));
        metadata.setContentType(StringUtils.hasText(file.getContentType())
                ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);

        PutObjectResult result = null;
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(cosConfig.getBucket(), objectKey, inputStream, metadata);
            request.setPicOperations(operations);
            result = cosClient.putObject(request);
        } catch (IOException | CosClientException exception) {
            log.error("Failed to upload file to COS, objectKey={}", objectKey, exception);
            ThrowUtils.throwException(ErrorCode.SERVICE_UNAVAILABLE, "文件上传失败，请稍后重试");
        }
        return result;
    }

    /**
     * 获取指定对象的下载输入流，由调用方读取并在结束时关闭以释放 COS 连接。
     * 接口调用方应先校验权限并设置下载响应头，再通过 try-with-resources 将流写入响应体。
     * 流读取阶段的 IOException 由调用方处理，包括客户端断开连接的情况。
     *
     * @param objectKey 存储桶内的完整对象键，例如 images/uuid.jpg
     * @return 已打开的 COS 对象输入流，调用方负责关闭
     */
    public InputStream downloadFile(String objectKey) {
        ThrowUtils.throwIf(!StringUtils.hasText(objectKey), ErrorCode.VALIDATION_FAILED, "对象键不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(cosConfig.getBucket()), ErrorCode.INTERNAL_ERROR, "COS 存储桶未配置");

        InputStream inputStream = null;
        try {
            GetObjectRequest request = new GetObjectRequest(cosConfig.getBucket(), objectKey);
            // 将流的生命周期交给调用方，确保写入 HTTP 响应时连接仍然有效。
            inputStream = cosClient.getObject(request).getObjectContent();
        } catch (CosClientException exception) {
            log.error("Failed to download file from COS, objectKey={}", objectKey, exception);
            ThrowUtils.throwException(ErrorCode.SERVICE_UNAVAILABLE, "文件下载失败，请稍后重试");
        }
        return inputStream;
    }

    /**
     * 根据本项目图片 URL 读取对象属性，以便独立上传后只提交 URL 即可保存图片信息。
     * URL 仅用于解析对象键，云请求始终通过配置的 COS 客户端发起。
     *
     * @param imageUrl 本项目图片存储路径下的原图 URL，可包含签名参数
     * @return 原图、压缩图对象键及云端读取的真实图片属性
     */
    public ImageUploadVo readUploadedImage(String imageUrl) {
        ThrowUtils.throwIf(!StringUtils.hasText(imageUrl), ErrorCode.VALIDATION_FAILED, "图片 URL 不能为空");
        URI uri = null;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException exception) {
            ThrowUtils.throwException(ErrorCode.VALIDATION_FAILED, "图片 URL 格式无效");
        }
        String path = uri.getPath();
        ThrowUtils.throwIf(!"https".equalsIgnoreCase(uri.getScheme()) || path == null
                        || !path.matches("/images/[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp|gif|bmp|tif|tiff)"),
                ErrorCode.VALIDATION_FAILED, "请使用图片上传接口返回的原图 URL");
        String objectKey = path.substring(1);
        URI expected = URI.create(cosClient.getObjectUrl(cosConfig.getBucket(), objectKey).toExternalForm());
        ThrowUtils.throwIf(!expected.getHost().equalsIgnoreCase(uri.getHost()) || expected.getPort() != uri.getPort()
                        || uri.getUserInfo() != null,
                ErrorCode.VALIDATION_FAILED, "图片 URL 不属于当前存储桶");
        String thumbnailKey = objectKey.substring(0, objectKey.lastIndexOf('.')) + "_compressed.webp";
        ImageUploadVo result = null;
        try {
            ObjectMetadata metadata = cosClient.getObjectMetadata(cosConfig.getBucket(), objectKey);
            cosClient.getObjectMetadata(cosConfig.getBucket(), thumbnailKey);
            GetObjectRequest request = new GetObjectRequest(cosConfig.getBucket(), objectKey);
            request.putCustomQueryParameter("imageInfo", null);
            try (InputStream input = cosClient.getObject(request).getObjectContent()) {
                var info = objectMapper.readTree(input);
                ThrowUtils.throwIf(info.path("width").asInt() <= 0 || info.path("height").asInt() <= 0
                                || !StringUtils.hasText(info.path("format").asText()),
                        ErrorCode.SERVICE_UNAVAILABLE, "云端图片信息无效");
                result = new ImageUploadVo();
                result.setWidth(info.path("width").asInt());
                result.setHeight(info.path("height").asInt());
                result.setFormat(info.path("format").asText().toLowerCase(Locale.ROOT));
            }
            result.setOriginalObjectKey(objectKey);
            result.setThumbnailObjectKey(thumbnailKey);
            result.setFileSize(metadata.getContentLength());
            String encodedName = metadata.getUserMetaDataOf("original-filename");
            // 历史对象缺少原文件名元数据时，使用对象文件名以兼容已有上传结果。
            result.setOriginalFilename(encodedName == null ? StringUtils.getFilename(objectKey)
                    : new String(Base64.getUrlDecoder().decode(encodedName), StandardCharsets.UTF_8));
        } catch (CosServiceException exception) {
            ThrowUtils.throwIf(exception.getStatusCode() == 404, ErrorCode.RESOURCE_NOT_FOUND, "上传图片对象不存在");
            log.error("Failed to read COS image, objectKey={}", objectKey, exception);
            ThrowUtils.throwException(ErrorCode.SERVICE_UNAVAILABLE, "读取图片信息失败");
        } catch (IOException | CosClientException exception) {
            log.error("Failed to read COS image, objectKey={}", objectKey, exception);
            ThrowUtils.throwException(ErrorCode.SERVICE_UNAVAILABLE, "读取图片信息失败");
        }
        return result;
    }

    /**
     * 获取指定对象的长期访问地址。存储桶为公有读，地址不包含签名参数，可长期直接使用。
     *
     * @param objectKey 存储桶内的完整对象键，例如 images/uuid.jpg
     * @return 可直接访问的 HTTPS 地址
     */
    public String getObjectUrl(String objectKey) {
        ThrowUtils.throwIf(!StringUtils.hasText(objectKey), ErrorCode.VALIDATION_FAILED, "对象键不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(cosConfig.getBucket()), ErrorCode.INTERNAL_ERROR, "COS 存储桶未配置");
        return cosClient.getObjectUrl(cosConfig.getBucket(), objectKey).toExternalForm();
    }
}
