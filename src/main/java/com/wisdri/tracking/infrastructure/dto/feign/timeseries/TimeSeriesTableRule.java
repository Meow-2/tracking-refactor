package com.wisdri.tracking.infrastructure.dto.feign.timeseries;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 时序数据存储服务表列定义。
 */
@Data
@AllArgsConstructor
public class TimeSeriesTableRule {
    /**
     * 列名。
     */
    private String id;

    /**
     * 数据类型。
     */
    private String datatype;

    /**
     * 是否为 tag。
     */
    @JsonProperty("isTag")
    private Boolean isTag;
}
