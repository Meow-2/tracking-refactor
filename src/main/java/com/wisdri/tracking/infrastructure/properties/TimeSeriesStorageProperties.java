package com.wisdri.tracking.infrastructure.properties;

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
    /**
     * 时序存储服务基础地址。
     */
    private String baseUrl;

    /**
     * 时序数据数据库名称。
     */
    private String database;

    /**
     * Feign 连接超时时间，单位毫秒。
     */
    private Integer connectTimeout = 3000;

    /**
     * Feign 读取超时时间，单位毫秒。
     */
    private Integer readTimeout = 5000;
}
