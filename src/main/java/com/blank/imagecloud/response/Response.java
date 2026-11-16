package com.blank.imagecloud.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "REST API 通用响应")
public class Response<T> {
    @Schema(description = "业务响应码", requiredMode = Schema.RequiredMode.REQUIRED)
    int code;

    @Schema(description = "接口返回的数据")
    T data;

    @Schema(description = "可选的响应提示")
    String message;

    public Response(int code, T data) {
        this(code, data, null);
    }

    @JsonCreator
    public Response(@JsonProperty("code") int code,
                    @JsonProperty("data") T data,
                    @JsonProperty("message") String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }
}
