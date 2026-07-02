package com.wisdri.tracking.infrastructure.service.feign.client;

import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeRequest;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.concurrent.TimeUnit;

/**
 * Cube API OpenFeign 客户端。
 */
@FeignClient(
        name = "cube-api",
        url = "${cube-api.base-url}",
        configuration = CubeApiFeignClient.CubeApiFeignConfig.class
)
public interface CubeApiFeignClient {
    /**
     * 拉取多维度配置树。
     */
    @PostMapping("/openapi/meta/tree")
    R<CubeApiTreeResponse> fetchConfigTree(@RequestBody CubeApiTreeRequest request);

    /**
     * Cube API 专属 Feign 超时配置。
     */
    class CubeApiFeignConfig {
        @Bean
        public Request.Options cubeApiRequestOptions(@Value("${cube-api.connectTimeout:3000}") int connectTimeout,
                                                     @Value("${cube-api.readTimeout:5000}") int readTimeout) {
            return new Request.Options(connectTimeout, TimeUnit.MILLISECONDS, readTimeout, TimeUnit.MILLISECONDS, true);
        }
    }
}
