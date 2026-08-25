package com.wisdri.tracking.infrastructure.properties.feign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * quality.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "quality")
public class QualityProperties {
    private String baseUrl;
    private Integer connectTimeout = 3000;
    private Integer readTimeout = 5000;
}
