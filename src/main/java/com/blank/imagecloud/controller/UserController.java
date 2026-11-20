package com.blank.imagecloud.controller;

import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.common.CurrentUserContext;
import com.blank.imagecloud.models.dto.user.PublicUserSearchRequest;
import com.blank.imagecloud.models.dto.user.UserUpdateRequest;
import com.blank.imagecloud.models.dto.user.UserPasswordUpdateRequest;
import com.blank.imagecloud.response.Response;
import com.blank.imagecloud.service.UserService;
import com.blank.imagecloud.models.vo.user.PublicUserSearchPageVo;
import com.blank.imagecloud.models.vo.user.PublicUserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供用户基本资料查询和本人资料、密码修改。 */
@Tag(name = "用户资料")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * 修改登录用户本人的非空资料字段，身份由服务端登录态确定。
     *
     * @param request 已校验的个人资料，null 字段保持原值
     * @return 表示更新成功的业务响应
     */
    @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "修改本人资料", description = "局部更新本人资料，目标用户取自登录态。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> updateOwnProfile(@Valid @RequestBody UserUpdateRequest request) {
        userService.updateOwnProfile(CurrentUserContext.requireUserId(), request);
        return ResultUtil.ok();
    }

    /**
     * 校验原密码后修改登录用户本人的密码。
     *
     * @param request 已校验的原密码和新密码
     * @return 表示修改成功的业务响应
     */
    @PatchMapping(value = "/me/password", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "修改本人密码", description = "校验原密码后修改本人密码。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<Void> updateOwnPassword(@Valid @RequestBody UserPasswordUpdateRequest request) {
        userService.updateOwnPassword(CurrentUserContext.requireUserId(), request);
        return ResultUtil.ok();
    }

    /**
     * 按昵称分页搜索正常账号，返回供选择用户主页的公开候选项。
     *
     * @param request 昵称关键词与分页参数
     * @return 包含当前页候选、总数和分页参数的业务响应
     */
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按昵称搜索用户",
            description = "按昵称片段分页搜索正常账号，昵称完全一致者优先，每个结果附带最多三张审核通过的图片地址。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<PublicUserSearchPageVo> searchUsers(@Valid @RequestBody PublicUserSearchRequest request) {
        return ResultUtil.ok(userService.searchPublicUsers(request));
    }

    /**
     * 为已登录的调用方按 ID 返回正常账号的公开资料。
     *
     * @param id 目标用户 ID
     * @return 包含登录账号、昵称和可用头像地址的业务响应
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按 ID 获取公开用户资料", description = "查询正常账号的公开资料。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "业务码 200 表示成功，其余返回对应业务错误码。")
    public Response<PublicUserVo> getUser(@Parameter(description = "目标用户 ID", example = "42")
                                         @PathVariable("id") Long id) {
        return ResultUtil.ok(userService.getPublicUser(id));
    }
}
