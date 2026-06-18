package com.wisdri.tracking.infrastructure.service.feign.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cube API OpenFeign 客户端。
 */
@FeignClient(
        name = "cube-api",
        url = "${cube-api.base-url}"
)
public interface CubeApiFeignClient {
    /**
     * 拉取多维度配置树。
     */
    @PostMapping("/openapi/meta/tree")
    R<JsonNode> fetchConfigTree(@RequestBody CubeApiTreeRequest request);
}
