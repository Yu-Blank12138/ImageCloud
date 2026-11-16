package com.blank.imagecloud.controller.system;

import com.blank.imagecloud.common.ResultUtil;
import com.blank.imagecloud.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统状态")
@RestController
public class HeartbeatController {

    @GetMapping(value = "/api/heartbeat", produces = "application/json")
    @Operation(summary = "心跳检测", description = "检查应用是否能够响应 HTTP 请求。")
    @ApiResponse(
            responseCode = "200",
            description = "应用可响应",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"code\":200}"))
    )
    public Response<Void> heartbeat() {
        return ResultUtil.ok();
    }
}
