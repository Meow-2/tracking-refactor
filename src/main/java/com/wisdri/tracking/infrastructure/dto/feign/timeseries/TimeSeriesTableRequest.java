package com.wisdri.tracking.infrastructure.dto.feign.timeseries;

import lombok.Data;

import java.util.List;

/**
 * 时序数据存储服务创表请求。
 */
@Data
public class TimeSeriesTableRequest {
    /**
     * 存储方式，column 表示列存，row 表示行存。
     */
    private String mode;

    /**
     * 数据库名。
     */
    private String bucket;

    /**
     * 数据表名。
     */
    private String measurement;

    /**
     * 表列规则。
     */
    private List<TimeSeriesTableRule> rule;
}
