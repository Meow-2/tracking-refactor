package com.wisdri.tracking.infrastructure.service.feign.config;

import com.wisdri.tracking.infrastructure.properties.TimeSeriesStorageProperties;
import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * 时序存储服务专属 Feign 配置。
 * <p>
 * 该类由对应 FeignClient 显式引用，不注册为全局配置，避免影响其他客户端。
 */
public class TimeSeriesStorageFeignConfig {
    /**
     * 根据时序存储属性创建请求超时配置。
     */
    @Bean
    public Request.Options timeSeriesStorageRequestOptions(
            TimeSeriesStorageProperties properties) {
        return new Request.Options(
                properties.getConnectTimeout(),
                TimeUnit.MILLISECONDS,
                properties.getReadTimeout(),
                TimeUnit.MILLISECONDS,
                true
        );
    }
}
