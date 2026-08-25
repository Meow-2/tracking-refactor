package com.wisdri.tracking.infrastructure.service.feign.client;

import com.wisdri.tracking.infrastructure.service.feign.config.QualityFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 质量服务 OpenFeign 客户端。
 */
@FeignClient(
        name = "quality",
        url = "${quality.base-url}",
        configuration = QualityFeignConfig.class
)
public interface QualityFeignClient {
}
