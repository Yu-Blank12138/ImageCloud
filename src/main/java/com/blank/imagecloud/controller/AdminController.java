package com.blank.imagecloud.controller;

import com.blank.imagecloud.annotation.AuthCheck;
import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.constant.PlatformRole;
import com.blank.imagecloud.models.dto.user.AdminCreateUserRequest;
import com.blank.imagecloud.models.dto.user.AdminUpdateUserRequest;
import com.blank.imagecloud.models.dto.user.UserQueryRequest;
import com.blank.imagecloud.response.Response;
import com.blank.imagecloud.service.UserService;
import com.blank.imagecloud.models.vo.user.AdminUserPageVo;
import com.blank.imagecloud.models.vo.user.AdminUserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RestController;

/** 统一提供管理员的用户管理接口，图片管理接口见 {@link ImageController}。 */
@Tag(name = "管理员管理")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;

    /**
     * 使用默认初始密码创建管理员指定角色和状态的用户账号。
     *
     * @param request 用户创建资料，初始密码由服务端统一设置
     * @return 表示创建成功的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @PostMapping(value = "/users", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员创建用户", description = "创建账号，初始密码统一为 12345678。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        userService.createUser(request);
        return ResultUtil.ok();
    }

    /**
     * 局部更新其他用户的资料，成功后返回业务码。
     *
     * @param request 本次提交的可更新字段
     * @return 表示更新成功的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @PatchMapping(value = "/users", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员局部更新用户",
            description = "局部更新用户资料，目标用户由请求体 id 指定；设为 VIP 时必须同时提交到期日期。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> updateUser(@Valid @RequestBody AdminUpdateUserRequest request) {
        Long operatorId = CurrentUserContext.requireUserId();
        userService.updateUser(operatorId, request);
        return ResultUtil.ok();
    }

    /**
     * 按 ID 逻辑删除其他用户。
     *
     * @param id 目标用户 ID
     * @return 表示删除成功的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @DeleteMapping(value = "/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员逻辑删除用户", description = "按 ID 逻辑删除用户。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> deleteUser(@Parameter(description = "目标用户 ID", example = "42")
                                     @PathVariable("id") Long id) {
        Long operatorId = CurrentUserContext.requireUserId();
        userService.deleteUser(operatorId, id);
        return ResultUtil.ok();
    }

    /**
     * 绑定用户查询对象，并按条件筛选和分页读取管理员可见的用户资料。
     *
     * @param request URL 中的用户筛选条件和分页参数
     * @param response 用于设置缓存控制响应头的 Servlet 响应
     * @return 包含用户记录、总数和分页参数的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员分页查询用户", description = "按条件分页查询用户，结果按用户 ID 倒序。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<AdminUserPageVo> pageUsers(
            @Valid @ModelAttribute @ParameterObject UserQueryRequest request,
            HttpServletResponse response) {
        AdminUserPageVo pageResult = userService.pageAdminUsers(request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(pageResult);
    }

    /**
     * 按 ID 返回管理员可见的用户详情。
     *
     * @param id 目标用户 ID
     * @param response 用于设置缓存控制响应头的 Servlet 响应
     * @return 包含用户详情的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @GetMapping(value = "/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按 ID 获取管理员用户详情",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<AdminUserVo> getUser(@Parameter(description = "目标用户 ID", example = "42")
                                        @PathVariable("id") Long id, HttpServletResponse response) {
        AdminUserVo user = userService.getAdminUser(id);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(user);
    }
}
