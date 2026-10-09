package com.wisdri.tracking.infrastructure.properties.feign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** quality.* 质量服务超时参数；服务地址由 Feign 注解解析。 */
@Data
@Component
@ConfigurationProperties(prefix = "quality")
public class QualityServiceProperties {
    /** 连接超时，单位毫秒，默认 3000。 */
    private Integer connectTimeout = 3000;
    /** 响应超时，单位毫秒，默认 5000。 */
    private Integer readTimeout = 5000;
}
