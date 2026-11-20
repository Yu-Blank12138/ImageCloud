package com.blank.imagecloud.models.dto.user;

import com.blank.imagecloud.common.StrictStringDeserializer;
import com.blank.imagecloud.constant.UserConstant;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

/** 用户本人修改个人资料，省略或为 null 的字段保持原值。 */
@Getter
@Setter
@Schema(description = "用户本人更新资料请求")
public class UserUpdateRequest {
    /** 用户设置的新昵称，空字符串用于清空昵称。 */
    @Size(max = UserConstant.DISPLAY_NAME_MAX_LENGTH)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "可选新昵称，最多 12 个字符，空字符串清空")
    private String displayName;

    /** 压缩头像的 HTTPS 固定地址，空字符串用于清空头像。 */
    @URL(protocol = "https")
    @Size(max = 2048)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "可选压缩头像 HTTPS URL，空字符串清空", format = "uri")
    private String avatarUrl;

    /** 头像原图的 HTTPS 固定地址，空字符串用于清空。 */
    @URL(protocol = "https")
    @Size(max = 2048)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "可选头像原图 HTTPS URL，省略或 null 保持原值，空字符串清空", format = "uri")
    private String avatarOriginalUrl;

    /** 用户性别，0 未设置，1 男，2 女。 */
    @Min(0)
    @Max(2)
    @Schema(description = "性别，0 未设置，1 男，2 女", allowableValues = {"0", "1", "2"})
    private Integer gender;

    /** 用户自我介绍，空字符串可清空。 */
    @Size(max = UserConstant.BIO_MAX_LENGTH)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "自我介绍，最多 128 个字符")
    private String bio;

    /** 用户公开联系邮箱，空字符串可清空。 */
    @Pattern(regexp = UserConstant.EMAIL_PATTERN)
    @Size(max = UserConstant.EMAIL_MAX_LENGTH)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "联系邮箱，须为 name@example.com 格式，空字符串可清空", format = "email")
    private String email;

    /** 用户手机号，仅本人和管理员可见。 */
    @Pattern(regexp = UserConstant.PHONE_NUMBER_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "手机号，6–20 位数字，可带国际区号前缀 +，空字符串可清空")
    private String phoneNumber;

    /** 用户出生年月，空字符串可清空。 */
    @Pattern(regexp = UserConstant.BIRTH_MONTH_PATTERN)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Schema(description = "出生年月，YYYY-MM，不能晚于当前年月", example = "2000-05")
    private String birthMonth;

    /**
     * 规整昵称首尾空白，保留 null 的跳过语义。
     *
     * @param displayName 用户提交的新昵称，可为 null
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.strip();
    }
}
