package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.history.TrackingHistoryMetadata;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeRequest;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import com.wisdri.tracking.infrastructure.properties.feign.CubeApiProperties;
import com.wisdri.tracking.infrastructure.service.feign.client.CubeApiFeignClient;
import com.wisdri.tracking.infrastructure.service.feign.converter.CubeApiTrackingConfigConverterDispatcher;
import com.wisdri.tracking.infrastructure.service.feign.converter.TrackingHistoryMetadataConverter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class CubeApiGatewayTest {
    private final CubeApiFeignClient client = mock(CubeApiFeignClient.class);
    private final CubeApiTrackingConfigConverterDispatcher dispatcher = mock(CubeApiTrackingConfigConverterDispatcher.class);
    private final TrackingHistoryMetadataConverter history = mock(TrackingHistoryMetadataConverter.class);
    private final CubeApiGateway gateway = new CubeApiGateway();

    CubeApiGatewayTest() {
        CubeApiProperties properties = new CubeApiProperties();
        properties.setTreeRoot("/custom_tracking");
        ReflectionTestUtils.setField(gateway, "cubeApiFeignClient", client);
        ReflectionTestUtils.setField(gateway, "cubeApiTrackingConfigConverterDispatcher", dispatcher);
        ReflectionTestUtils.setField(gateway, "cubeApiProperties", properties);
        ReflectionTestUtils.setField(gateway, "historyMetadataConverter", history);
    }

    @Test
    void historicalFetchUsesExplicitUnitAndConfiguredTreeRequestWithoutRealtimeDispatcher() {
        CubeApiTreeResponse tree = new CubeApiTreeResponse();
        TrackingHistoryMetadata historyMetadata = mock(TrackingHistoryMetadata.class);
        when(client.fetchConfigTree(any())).thenReturn(R.data(tree));
        when(history.convert(tree, "zrm1", TrackingType.STATUS, "/custom_tracking")).thenReturn(historyMetadata);

        assertThat(gateway.fetchHistoryMetadata("zrm1", TrackingType.STATUS)).isSameAs(historyMetadata);
        ArgumentCaptor<CubeApiTreeRequest> request = ArgumentCaptor.forClass(CubeApiTreeRequest.class);
        verify(client).fetchConfigTree(request.capture());
        assertThat(request.getValue().getPath()).isEqualTo("/custom_tracking");
        assertThat(request.getValue().getParaRange()).isEqualTo(3);
        assertThat(request.getValue().getPathHeader()).isFalse();
        assertThat(request.getValue().getOnlyDir()).isFalse();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void realtimeEntrypointUsesOriginalDispatcherWithoutHistoryConversion() {
        CubeApiTreeResponse tree = new CubeApiTreeResponse();
        Map<TrackingType, TrackingConfig> configs = Collections.emptyMap();
        when(client.fetchConfigTree(any())).thenReturn(R.data(tree));
        when(dispatcher.convert(tree)).thenReturn(configs);
        assertThat(gateway.fetchTrackingConfigs()).isSameAs(configs);
        verify(dispatcher).convert(tree);
        verifyNoInteractions(history);
    }

    @Test
    void failedAndNullCubeResponsesDoNotReachHistoryConversion() {
        when(client.fetchConfigTree(any())).thenReturn(R.fail());
        assertThatThrownBy(() -> gateway.fetchHistoryMetadata("zrm1", TrackingType.STATUS))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("配置树查询失败");
        when(client.fetchConfigTree(any())).thenReturn(null);
        assertThatThrownBy(() -> gateway.fetchHistoryMetadata("zrm1", TrackingType.STATUS))
                .isInstanceOf(ExternalServiceException.class);
        verifyNoInteractions(history, dispatcher);
    }
}
