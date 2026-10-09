package com.wisdri.tracking.infrastructure.properties.feign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** quality.* 质量服务连接参数；部署环境可覆盖 QUALITY_BASE_URL。 */
@Data
@Component
@ConfigurationProperties(prefix = "quality")
public class QualityServiceProperties {
    /** 质量服务或网关根地址，不含接口路径。 */
    private String baseUrl;
    /** 连接超时，单位毫秒，默认 3000。 */
    private Integer connectTimeout = 3000;
    /** 响应超时，单位毫秒，默认 5000。 */
    private Integer readTimeout = 5000;
}
