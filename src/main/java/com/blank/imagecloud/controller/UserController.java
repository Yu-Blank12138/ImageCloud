package com.blank.imagecloud.controller;

import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.response.Response;
import com.blank.imagecloud.service.UserService;
import com.blank.imagecloud.models.vo.PublicUserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供公开的用户基本资料查询。 */
@Tag(name = "公开用户资料")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * 为已登录的调用方按 ID 返回正常账号的公开资料。
     *
     * @param id 目标用户 ID
     * @return 包含用户 ID、昵称和可用头像地址的业务响应
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "按 ID 获取公开用户资料", description = "登录用户可查询正常且未删除账号的公开资料。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true,
            description = "业务码 200 表示成功，4000 表示 ID 格式错误，4001 表示 ID 无效，"
                    + "4010 表示缺少令牌，4012 表示登录态无效，4040 表示用户不存在、已停用或已删除，"
                    + "5030 表示暂无法认证。")
    public Response<PublicUserVo> getUser(@Parameter(description = "目标用户 ID", example = "42")
                                         @PathVariable("id") Long id) {
        return ResultUtil.ok(userService.getPublicUser(id));
    }
}
