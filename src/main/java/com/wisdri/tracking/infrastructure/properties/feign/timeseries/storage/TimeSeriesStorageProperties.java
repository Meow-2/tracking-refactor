package com.wisdri.tracking.infrastructure.properties.feign.timeseries.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * time-series-storage.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "time-series-storage")
public class TimeSeriesStorageProperties {
    private String baseUrl;
    private String database;
    private Integer connectTimeout = 3000;
    private Integer readTimeout = 5000;
}
