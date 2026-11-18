package com.blank.imagecloud.models.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** 各业务查询请求共用的分页参数。 */
@Getter
@Setter
@Schema(description = "通用分页请求")
public class PageRequest {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /** 从 1 开始的页码。 */
    @Min(1)
    @Schema(description = "页码，从 1 开始", defaultValue = "1", example = "1", minimum = "1")
    private Integer page = DEFAULT_PAGE;

    /** 每页记录数，限制单次查询的数据量。 */
    @Min(1)
    @Max(MAX_PAGE_SIZE)
    @Schema(description = "每页条数，范围 1–100", defaultValue = "20", example = "20",
            minimum = "1", maximum = "100")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 设置页码，空参数沿用默认页码。
     *
     * @param page 查询页码，省略或空值时使用 1
     */
    public void setPage(Integer page) {
        this.page = page == null ? DEFAULT_PAGE : page;
    }

    /**
     * 设置每页条数，空参数沿用默认数量。
     *
     * @param pageSize 每页记录数，省略或空值时使用 20
     */
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
