package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.service.feign.converter.CubeApiTrackingConfigConverterDispatcher;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeRequest;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import com.wisdri.tracking.infrastructure.properties.feign.CubeApiProperties;
import com.wisdri.tracking.infrastructure.service.feign.client.CubeApiFeignClient;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

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
    private CubeApiTrackingConfigConverterDispatcher cubeApiTrackingConfigConverterDispatcher;

    /**
     * Cube API 配置树根路径。
     */
    @Resource
    private CubeApiProperties cubeApiProperties;

    /**
     * 拉取并转换跟踪配置。
     */
    public Map<TrackingType, TrackingConfig> fetchTrackingConfigs() {
        R<CubeApiTreeResponse> response =
                cubeApiFeignClient.fetchConfigTree(
                        CubeApiTreeRequest.defaultRequest(cubeApiProperties.getTreeRoot()));
        if (!R.isSuccess(response)) {
            throw new ExternalServiceException("Cube API 配置树查询失败");
        }
        return cubeApiTrackingConfigConverterDispatcher.convert(response.getData());
    }
}
