package com.blank.imagecloud.controller;

import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统状态")
@RestController
public class HeartbeatController {

    /**
     * 为已登录的调用方返回应用响应状态。
     *
     * @return 表示应用可响应的通用成功结果
     */
    @GetMapping(value = "/api/heartbeat", produces = "application/json")
    @Operation(summary = "心跳检测", description = "为已登录的调用方检查应用是否能够响应 HTTP 请求。",
            security = @SecurityRequirement(name = AuthController.BEARER_AUTH_SCHEME))
    @ApiResponse(
            responseCode = "200",
            description = "业务码 200 表示应用可响应，其余返回对应业务错误码。",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"code\":200}"))
    )
    public Response<Void> heartbeat() {
        return ResultUtil.ok();
    }
}
