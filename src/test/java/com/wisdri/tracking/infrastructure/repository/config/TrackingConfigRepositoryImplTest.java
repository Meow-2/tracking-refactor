package com.wisdri.tracking.infrastructure.repository.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.config.ConvertedTrackingConfig;
import com.wisdri.tracking.infrastructure.service.feign.gateway.CubeApiGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingConfigRepositoryImplTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CubeApiGateway cubeApiGateway;

    @Test
    void refreshLoadsAllConvertedConfigsIntoLocalCache() {
        TrackingConfigRepositoryImpl repository = new TrackingConfigRepositoryImpl();
        ReflectionTestUtils.setField(repository, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(repository, "cubeApiGateway", cubeApiGateway);

        ObjectNode cp1Config = processConfig(true, "topic/cp1");
        ObjectNode zrm1Config = processConfig(false, "topic/zrm1");
        Map<String, String> redis = new HashMap<>();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cubeApiGateway.fetchTrackingConfigs()).thenReturn(Arrays.asList(
                new ConvertedTrackingConfig("CP1", TrackingType.PROCESS, cp1Config),
                new ConvertedTrackingConfig("ZRM1", TrackingType.PROCESS, zrm1Config)
        ));
        when(valueOperations.get("tracking:cp1:process:config")).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        when(valueOperations.get("tracking:zrm1:process:config")).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(any(String.class), any(String.class));

        repository.refresh();

        assertThat(repository.findAs("CP1", TrackingType.PROCESS, ProcessTrackingConfig.class))
                .get()
                .extracting(ProcessTrackingConfig::getMqttTopic)
                .isEqualTo("topic/cp1");
        assertThat(repository.findAs("ZRM1", TrackingType.PROCESS, ProcessTrackingConfig.class))
                .get()
                .extracting(ProcessTrackingConfig::getEnable)
                .isEqualTo(false);
    }

    private ObjectNode processConfig(boolean enable, String mqttTopic) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("enable", enable);
        config.put("mqtt_topic", mqttTopic);
        config.set("tracking", objectMapper.createObjectNode());
        config.putArray("segments");
        return config;
    }
}
