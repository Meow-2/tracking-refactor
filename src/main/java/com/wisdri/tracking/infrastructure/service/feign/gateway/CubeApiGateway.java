package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.service.feign.converter.CubeApiTrackingConfigConverter;
import com.wisdri.tracking.infrastructure.dto.feign.cube.ConvertedTrackingConfig;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeRequest;
import com.wisdri.tracking.infrastructure.service.feign.client.CubeApiFeignClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * Cube API 访问网关。
 */
@Component
public class CubeApiGateway {
    /**
     * Cube API Feign 客户端。
     */
    @Resource
    private CubeApiFeignClient cubeApiFeignClient;

    /**
     * Cube API 配置树转换器。
     */
    @Resource
    private CubeApiTrackingConfigConverter cubeApiTrackingConfigConverter;

    /**
     * Cube API 配置树根路径。
     */
    @Value("${cube-api.tree-root:/aygg_tracking}")
    private String cubeApiTreeRoot;

    /**
     * 拉取并转换跟踪配置。
     */
    public List<ConvertedTrackingConfig> fetchTrackingConfigs() {
        R<JsonNode> response = cubeApiFeignClient.fetchConfigTree(CubeApiTreeRequest.defaultRequest(cubeApiTreeRoot));
        if (!R.isSuccess(response)) {
            throw new ExternalServiceException("Cube API 配置树查询失败");
        }
        return cubeApiTrackingConfigConverter.convert(response.getData());
    }
}
