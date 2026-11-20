package com.blank.imagecloud.models.dto.image;

import com.blank.imagecloud.constant.ImageConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员审核图片的请求，通过 JSON 请求体提交。
 * 审核人由登录态决定，请求体不接收审核人 ID，避免审核记录被伪造。
 */
@Getter
@Setter
@Schema(description = "管理员审核图片请求")
public class AdminImageAuditRequest {

    /** 目标图片 ID，修改类请求的 ID 统一放在请求体中。 */
    @NotNull
    @Positive
    @Schema(description = "目标图片 ID", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    /** 审核结论，只允许通过或不通过。 */
    @NotNull
    @Min(1)
    @Max(2)
    @Schema(description = "审核结论，1 通过，2 不通过；0 表示未审核，不能作为审核结果提交",
            allowableValues = {"1", "2"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer auditStatus;

    /** 审核信息，不通过时必填，通过时忽略并清空历史信息。 */
    @Size(max = ImageConstant.AUDIT_MESSAGE_MAX_LENGTH)
    @Schema(description = "审核信息，审核不通过时必填，最多 1024 个字符；审核通过时忽略并清空历史信息",
            nullable = true)
    private String auditMessage;
}
