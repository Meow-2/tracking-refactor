package com.wisdri.tracking.infrastructure.service.feign.client;

import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeRequest;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import com.wisdri.tracking.infrastructure.service.feign.config.CubeApiFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cube API OpenFeign 客户端。
 */
@FeignClient(
        name = "cube-api",
        url = "${cube-api.base-url}",
        configuration = CubeApiFeignConfig.class
)
public interface CubeApiFeignClient {
    /**
     * 拉取多维度配置树。
     */
    @PostMapping("/openapi/meta/tree")
    R<CubeApiTreeResponse> fetchConfigTree(@RequestBody CubeApiTreeRequest request);

}
