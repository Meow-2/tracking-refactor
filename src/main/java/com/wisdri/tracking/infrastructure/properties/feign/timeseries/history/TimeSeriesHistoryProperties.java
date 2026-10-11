package com.wisdri.tracking.infrastructure.properties.feign.timeseries.history;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 历史查询独立配置，超时单位为毫秒。 */
@Data
@Component
@ConfigurationProperties(prefix = "time-series-history")
public class TimeSeriesHistoryProperties {
    private String baseUrl;
    private String database = "cube";
    private Integer connectTimeout = 3000;
    private Integer readTimeout = 60000;
}
