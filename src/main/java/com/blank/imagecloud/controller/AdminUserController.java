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
import com.blank.imagecloud.models.vo.AdminUserPageVo;
import com.blank.imagecloud.models.vo.AdminUserVo;
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

/** 提供需要管理员权限的用户管理接口。 */
@Tag(name = "管理员用户管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;

    /**
     * 使用默认初始密码创建管理员指定角色和状态的用户账号。
     *
     * @param request 用户创建资料，初始密码由服务端统一设置
     * @return 表示创建成功的业务响应
     */
    @AuthCheck(PlatformRole.ADMIN)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员创建用户",
            description = "初始密码由服务端统一设置为 12345678，成功响应仅包含业务码 200。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示成功，4000 或 4001 表示输入无效，4010 或 4012 表示登录态无效，"
                    + "4030 表示权限不足，4090 表示登录账号已被占用，5030 表示认证暂不可用。")
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
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员局部更新用户",
            description = "请求体中的 id 指定目标用户，其他资料字段省略或为 null 时保持原值。"
                    + "设置 VIP 角色时必须同时传入 vipExpiresAt，成功响应仅包含业务码 200。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示成功，4000 或 4001 表示输入无效，4010 或 4012 表示登录态无效，"
                    + "4030 表示权限不足或试图修改自己的账号，4040 表示用户不存在，4090 表示登录账号冲突，"
                    + "5030 表示认证暂不可用。")
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
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员逻辑删除用户",
            description = "按 ID 逻辑删除其他用户。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示成功，4000 或 4001 表示 ID 无效，4010 或 4012 表示登录态无效，"
                    + "4030 表示权限不足或试图删除自己的账号，4040 表示用户不存在或已删除，"
                    + "5030 表示认证暂不可用。")
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
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员分页查询用户",
            description = "通过 URL 提交查询条件。账号和昵称按字面包含匹配，ID、角色和状态精确匹配，"
                    + "创建时间支持包含边界的范围筛选，多个条件同时生效。"
                    + "省略或空白的条件不参与筛选。账号区分大小写，片段允许 1–32 位英文字母、数字或下划线。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示成功，4000 表示参数格式错误，4001 表示查询条件或分页参数无效，"
                    + "4010 或 4012 表示登录态无效，4030 表示权限不足，5030 表示认证暂不可用。")
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
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按 ID 获取管理员用户详情",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示成功，4000 表示 ID 格式错误，4001 表示 ID 无效，"
                    + "4010 表示缺少令牌，4012 表示登录态失效，4030 表示权限不足，"
                    + "4040 表示用户不存在或已删除，5030 表示认证暂不可用。")
    public Response<AdminUserVo> getUser(@Parameter(description = "目标用户 ID", example = "42")
                                        @PathVariable("id") Long id, HttpServletResponse response) {
        AdminUserVo user = userService.getAdminUser(id);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtil.ok(user);
    }
}
