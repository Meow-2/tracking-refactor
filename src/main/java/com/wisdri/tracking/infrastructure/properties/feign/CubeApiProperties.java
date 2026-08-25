package com.wisdri.tracking.infrastructure.properties.feign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * cube-api.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cube-api")
public class CubeApiProperties {
    private String baseUrl;
    private String treeRoot = "/aygg_tracking";
    private Integer connectTimeout = 3000;
    private Integer readTimeout = 5000;
}
