package com.blank.imagecloud.response;

import com.blank.imagecloud.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "REST API 错误响应")
public record ErrorResponse(
        @Schema(description = "四位业务错误码", example = "4001")
        int code,
        @Schema(description = "可向客户端展示的错误提示", example = "请求参数校验失败")
        String message
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }
}
