package com.wisdri.tracking.infrastructure.properties.feign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** quality.* 质量服务连接参数；Feign 通过同名配置项解析服务地址。 */
@Data
@Component
@ConfigurationProperties(prefix = "quality")
public class QualityServiceProperties {
    /** 质量服务根地址，不含接口路径；部署时可用 QUALITY_BASEURL 覆盖 YAML 默认值。 */
    private String baseUrl;
    /** 连接超时，单位毫秒，默认 3000。 */
    private Integer connectTimeout = 3000;
    /** 响应超时，单位毫秒，默认 5000。 */
    private Integer readTimeout = 5000;
}
