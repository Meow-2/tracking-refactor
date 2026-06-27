package com.wisdri.tracking.infrastructure.repository.config;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.service.feign.gateway.CubeApiGateway;
import com.wisdri.tracking.infrastructure.service.redis.RedisKeys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingConfigRepositoryImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void refreshOnlyCurrentYamlUnitConfigs() throws Exception {
        String cp1Key = RedisKeys.trackingConfig("cp1", TrackingType.PROCESS);
        String fcl1Key = RedisKeys.trackingConfig("FCL1", TrackingType.PROCESS);
        String cp1Json = "{\"enable\":true,\"mqtt_topic\":\"/cp1/process\",\"segments\":[{\"code\":\"nof\",\"name\":\"NOF段\"}]}";
        ProcessTrackingConfig cp1Config = ProcessTrackingConfig.builder()
                .unitCode("cp1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("/cp1/process")
                .segments(Collections.singletonList(SegmentConfig.builder().code("nof").name("NOF段").build()))
                .build();
        CubeApiGateway cubeApiGateway = mock(CubeApiGateway.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        TrackingResultRepositoryDispatcher trackingResultRepositoryDispatcher = mock(TrackingResultRepositoryDispatcher.class);
        TrackingConfigRepositoryImpl repository = new TrackingConfigRepositoryImpl();
        ReflectionTestUtils.setField(repository, "cubeApiGateway", cubeApiGateway);
        ReflectionTestUtils.setField(repository, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(repository, "trackingResultRepositoryDispatcher", trackingResultRepositoryDispatcher);
        when(cubeApiGateway.fetchTrackingConfigs())
                .thenReturn(Collections.singletonMap(TrackingType.PROCESS, cp1Config));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cp1Key)).thenReturn(cp1Json);

        repository.refresh();

        ArgumentCaptor<TrackingConfig> configCaptor = ArgumentCaptor.forClass(TrackingConfig.class);
        verify(valueOperations).set(eq(cp1Key), anyString());
        verify(valueOperations, never()).set(eq(fcl1Key), anyString());
        verify(trackingResultRepositoryDispatcher).createTable(configCaptor.capture());
        assertThat(configCaptor.getValue()).isInstanceOf(ProcessTrackingConfig.class);
        assertThat(configCaptor.getValue().getUnitCode()).isEqualTo("cp1");
        assertThat(configCaptor.getValue().getTrackingType()).isEqualTo(TrackingType.PROCESS);
    }
}
