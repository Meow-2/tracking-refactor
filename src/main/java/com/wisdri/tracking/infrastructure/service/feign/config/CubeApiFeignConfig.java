package com.wisdri.tracking.infrastructure.service.feign.config;

import com.wisdri.tracking.infrastructure.properties.feign.CubeApiProperties;
import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Cube API 专属 Feign 配置。
 */
public class CubeApiFeignConfig {
    @Bean
    public Request.Options cubeApiRequestOptions(CubeApiProperties properties) {
        return new Request.Options(
                properties.getConnectTimeout(),
                TimeUnit.MILLISECONDS,
                properties.getReadTimeout(),
                TimeUnit.MILLISECONDS,
                true
        );
    }
}
