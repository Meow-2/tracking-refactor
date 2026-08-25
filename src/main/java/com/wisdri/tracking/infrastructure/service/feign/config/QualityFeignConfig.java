package com.wisdri.tracking.infrastructure.service.feign.config;

import com.wisdri.tracking.infrastructure.properties.feign.QualityProperties;
import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * 质量服务专属 Feign 配置。
 */
public class QualityFeignConfig {
    @Bean
    public Request.Options qualityRequestOptions(QualityProperties properties) {
        return new Request.Options(
                properties.getConnectTimeout(),
                TimeUnit.MILLISECONDS,
                properties.getReadTimeout(),
                TimeUnit.MILLISECONDS,
                true
        );
    }
}
