package com.blank.imagecloud.controller;

import com.blank.imagecloud.annotation.AuthCheck;
import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.constant.PlatformRole;
import com.blank.imagecloud.models.dto.image.AdminImageAuditRequest;
import com.blank.imagecloud.models.dto.image.AdminImageCreateRequest;
import com.blank.imagecloud.models.dto.image.AdminImageQueryRequest;
import com.blank.imagecloud.models.dto.image.AdminImageUpdateRequest;
import com.blank.imagecloud.models.vo.image.AdminImagePageVo;
import com.blank.imagecloud.models.vo.image.AdminImageUploadVo;
import com.blank.imagecloud.models.vo.image.AdminImageVo;
import com.blank.imagecloud.models.vo.user.UserAvatarUploadVo;
import com.blank.imagecloud.response.Response;
import com.blank.imagecloud.service.ImageService;
import com.blank.imagecloud.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 统一提供用户头像和管理员图片管理接口。
 * 用户侧接口绑定登录态，管理员侧接口额外要求管理员角色。
 */
@Tag(name = "图片管理")
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {
    private final ImageService imageService;
    private final UserService userService;

    /**
     * 返回指定用户当前头像的公开原图地址，供前端点击头像后加载。
     *
     * @param id 目标用户 ID
     * @return data 为原图固定 URL 的业务响应
     */
    @GetMapping(value = "/avatars/{id}/original", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "获取用户头像原图", description = "返回用户头像原图的固定公开地址。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<String> getOriginalAvatar(
            @Parameter(description = "目标用户 ID", example = "42") @PathVariable("id") Long id) {
        return ResultUtil.ok(userService.getOriginalAvatarUrl(id));
    }

    /**
     * 上传登录用户的图像并返回前端可展示的访问地址。
     *
     * @param file multipart 表单中的图像文件
     * @param response 用于设置缓存控制响应头的响应
     * @return 包含压缩头像和原图固定 URL 的业务响应
     */
    @PostMapping(value = "/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "上传用户图像", description = "上传本人头像，返回压缩图与原图的固定地址，最多 20MB。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<UserAvatarUploadVo> uploadOwnImage(
            @Parameter(description = "图像文件，支持 jpg、jpeg、png、webp、gif、bmp、tif、tiff",
                    required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file, HttpServletResponse response) {
        UserAvatarUploadVo uploaded = userService.uploadOwnImage(CurrentUserContext.requireUserId(), file);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(uploaded);
    }

    /**
     * 独立上传图片并返回预览地址，供后续新增或修改请求引用。
     *
     * @param file 图片文件，最多 20MB
     * @param response 用于禁用上传结果缓存
     * @return 原图和压缩图的预览 URL
     */
    @AuthCheck(PlatformRole.ADMIN)
    @PostMapping(value = "/admin/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员独立上传图片", description = "上传图片并返回可复用的访问地址，最多 20MB。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<AdminImageUploadVo> uploadImage(
            @Parameter(description = "图片文件", required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file, HttpServletResponse response) {
        AdminImageUploadVo uploaded = imageService.uploadByAdmin(CurrentUserContext.requireUserId(), file);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(uploaded);
    }

    /**
     * 使用已上传图片的 URL 和 JSON 元数据创建图片记录。
     *
     * @param request 图片元数据及上传接口返回的 imageUrl
     * @return 表示创建成功的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @PostMapping(value = "/admin", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员添加图片", description = "使用已上传图片的 URL 创建图片记录，审核直接通过。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> createImage(@Valid @RequestBody AdminImageCreateRequest request) {
        imageService.createByAdmin(CurrentUserContext.requireUserId(), request);
        return ResultUtil.ok();
    }

    /**
     * 按 ID 逻辑删除图片。
     *
     * @param id 目标图片 ID
     * @return 表示删除成功的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @DeleteMapping(value = "/admin/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员逻辑删除图片", description = "按 ID 逻辑删除图片记录，对象存储中的文件保留。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> deleteImage(@Parameter(description = "目标图片 ID", example = "7")
                                      @PathVariable("id") Long id) {
        imageService.deleteByAdmin(CurrentUserContext.requireUserId(), id);
        return ResultUtil.ok();
    }

    /**
     * 按 ID 返回管理员可见的图片详情。
     *
     * @param id 目标图片 ID
     * @param response 用于设置缓存控制响应头的 Servlet 响应
     * @return 包含图片元数据、审核信息和长期访问地址的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @GetMapping(value = "/admin/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按 ID 获取管理员图片详情", description = "返回图片元数据、审核信息和图片固定访问地址。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<AdminImageVo> getImage(@Parameter(description = "目标图片 ID", example = "7")
                                           @PathVariable("id") Long id, HttpServletResponse response) {
        AdminImageVo image = imageService.getAdminImage(id);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(image);
    }

    /**
     * 绑定图片查询对象，并按条件筛选和分页读取管理员可见的图片详情。
     *
     * @param request URL 中的图片筛选条件和分页参数
     * @param response 用于设置缓存控制响应头的 Servlet 响应
     * @return 包含图片详情、总数和分页参数的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @GetMapping(value = "/admin", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员分页查询图片", description = "按条件分页查询图片，结果按图片 ID 倒序。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<AdminImagePageVo> pageImages(
            @Valid @ModelAttribute @ParameterObject AdminImageQueryRequest request,
            HttpServletResponse response) {
        AdminImagePageVo pageResult = imageService.pageAdminImages(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(pageResult);
    }

    /**
     * 使用 JSON 局部更新图片，可通过独立上传得到的 URL 替换图片。
     *
     * @param request 携带目标图片 ID 与本次提交字段，至少包含一个非空值字段
     * @return 表示更新成功的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @PatchMapping(value = "/admin", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员局部更新图片", description = "局部更新图片元数据，可选替换图片，目标 ID 由请求体指定。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> updateImage(@Valid @RequestBody AdminImageUpdateRequest request) {
        imageService.updateByAdmin(CurrentUserContext.requireUserId(), request);
        return ResultUtil.ok();
    }

    /**
     * 审核图片，审核人取自当前登录态。
     *
     * @param request 目标图片 ID、审核结论和审核信息
     * @return 表示审核成功的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @PatchMapping(value = "/admin/audit", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员审核图片", description = "审核图片，审核人取自登录态，支持改判。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> auditImage(@Valid @RequestBody AdminImageAuditRequest request) {
        imageService.auditByAdmin(CurrentUserContext.requireUserId(), request);
        return ResultUtil.ok();
    }
}
