package com.wisdri.tracking.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * tracking.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tracking")
public class TrackingProperties {
    /**
     * 当前实例负责的机组代码。
     */
    private String unit;

    /**
     * 是否启用第三方数据存储。
     */
    private Boolean dataStorage;
}
