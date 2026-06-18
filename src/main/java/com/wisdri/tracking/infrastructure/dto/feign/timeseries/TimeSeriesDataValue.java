package com.wisdri.tracking.infrastructure.dto.feign.timeseries;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 时序数据存储服务单列值。
 */
@Data
public class TimeSeriesDataValue {
    /**
     * 列名。
     */
    private String id;

    /**
     * 值。
     */
    private Object v;

    /**
     * 是否有效。
     */
    private Boolean q;

    /**
     * 时间戳。
     */
    private Long t;

    /**
     * 是否为 tag。
     */
    @JsonProperty("isTag")
    private Boolean isTag;
}
