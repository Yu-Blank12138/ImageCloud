package com.blank.imagecloud.exception;

/**
 * 定义 REST API 业务错误码和默认提示。
 */
public enum ErrorCode {
    INVALID_REQUEST(4000, "请求格式或参数类型错误"),
    VALIDATION_FAILED(4001, "请求参数校验失败"),
    AUTHENTICATION_REQUIRED(4010, "缺少访问令牌"),
    INVALID_CREDENTIALS(4011, "登录凭据错误"),
    INVALID_ACCESS_TOKEN(4012, "访问令牌无效"),
    ACCESS_DENIED(4030, "权限不足"),
    RESOURCE_NOT_FOUND(4040, "资源不存在"),
    METHOD_NOT_ALLOWED(4050, "请求方法不受支持"),
    CONFLICT(4090, "资源状态冲突"),
    EDIT_LEASE_CONFLICT(4091, "编辑租约冲突"),
    EDIT_VERSION_CONFLICT(4092, "图片版本冲突"),
    PAYLOAD_TOO_LARGE(4130, "请求体或上传文件过大"),
    UNSUPPORTED_MEDIA_TYPE(4150, "请求媒体类型不受支持"),
    INTERNAL_ERROR(5000, "服务端内部错误"),
    SERVICE_UNAVAILABLE(5030, "服务暂时不可用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
