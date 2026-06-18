package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.converter.config.CubeApiTrackingConfigConverter;
import com.wisdri.tracking.infrastructure.dto.config.ConvertedTrackingConfig;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeRequest;
import com.wisdri.tracking.infrastructure.service.feign.client.CubeApiFeignClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CubeApiGatewayTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CubeApiFeignClient cubeApiFeignClient;

    @Mock
    private CubeApiTrackingConfigConverter cubeApiTrackingConfigConverter;

    @Test
    void fetchTrackingConfigsReturnsConvertedConfigs() {
        CubeApiGateway gateway = new CubeApiGateway();
        ReflectionTestUtils.setField(gateway, "cubeApiFeignClient", cubeApiFeignClient);
        ReflectionTestUtils.setField(gateway, "cubeApiTrackingConfigConverter", cubeApiTrackingConfigConverter);
        ReflectionTestUtils.setField(gateway, "cubeApiTreeRoot", "/aygg_tracking");

        ObjectNode tree = objectMapper.createObjectNode();
        ObjectNode config = objectMapper.createObjectNode();
        ConvertedTrackingConfig convertedConfig = new ConvertedTrackingConfig("CP1", TrackingType.PROCESS, config);
        when(cubeApiFeignClient.fetchConfigTree(any(CubeApiTreeRequest.class))).thenReturn(R.data(tree));
        when(cubeApiTrackingConfigConverter.convert(tree)).thenReturn(Collections.singletonList(convertedConfig));

        List<ConvertedTrackingConfig> configs = gateway.fetchTrackingConfigs();

        assertThat(configs).containsExactly(convertedConfig);
    }
}
