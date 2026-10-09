package com.wisdri.tracking.infrastructure.service.feign.config;

import com.wisdri.tracking.infrastructure.properties.feign.QualityServiceProperties;
import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/** 质量服务专属 Feign 超时，不改变其他接口客户端。 */
public class QualityServiceFeignConfig {
    @Bean
    public Request.Options qualityServiceRequestOptions(QualityServiceProperties properties) {
        return new Request.Options(properties.getConnectTimeout(), TimeUnit.MILLISECONDS,
                properties.getReadTimeout(), TimeUnit.MILLISECONDS, true);
    }
}
