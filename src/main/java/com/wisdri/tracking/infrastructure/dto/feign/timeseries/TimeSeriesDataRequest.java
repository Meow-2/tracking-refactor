package com.wisdri.tracking.infrastructure.dto.feign.timeseries;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 时序数据存储服务列式写入请求。
 */
@Data
public class TimeSeriesDataRequest {
    /**
     * 批次时间戳。
     */
    private Long timestamp;

    /**
     * 是否使用相同设备时间。
     */
    @JsonProperty("isSameDeviceTime")
    private Boolean isSameDeviceTime;

    /**
     * 备注。
     */
    private Object mark;

    /**
     * 参数值列表。
     */
    private List<TimeSeriesDataValue> values;
}
