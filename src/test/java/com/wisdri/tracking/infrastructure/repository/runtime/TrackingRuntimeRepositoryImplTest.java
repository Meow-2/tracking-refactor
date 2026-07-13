package com.wisdri.tracking.infrastructure.repository.runtime;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.runtime.process.ProcessSegmentRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.service.feign.gateway.CubeApiGateway;
import com.wisdri.tracking.infrastructure.service.redis.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingRuntimeRepositoryImplTest {
    private final Map<String, String> redis = new ConcurrentHashMap<>();
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString());
    }

    @Test
    void savesSnakeCaseJsonAndRestoresRuntimeFromRedis() {
        TrackingRuntimeRepositoryImpl repository = repository();
        ProcessSegmentRuntime segment = ProcessSegmentRuntime.builder()
                .segmentCode("S1")
                .coilNo("C001")
                .headLength(new BigDecimal("12.30"))
                .build();
        ProcessTrackingRuntime runtime = ProcessTrackingRuntime.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .updatedAt(Instant.parse("2026-07-05T12:00:00Z"))
                .segments(Collections.singletonMap("S1", segment))
                .build();

        repository.saveRuntime(runtime);

        String json = redis.get("tracking:cp1:process:runtime");
        assertTrue(json.contains("\"head_length\""));

        TrackingRuntimeRepositoryImpl restartedRepository = repository();
        ProcessTrackingRuntime restored = restartedRepository.findRuntimeAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingRuntime.class
        ).orElseThrow(AssertionError::new);
        assertEquals("C001", restored.getSegments().get("S1").getCoilNo());
        assertEquals(new BigDecimal("12.30"), restored.getSegments().get("S1").getHeadLength());
    }

    @Test
    void isolatesRuntimeByUnitAndTrackingType() {
        TrackingRuntimeRepositoryImpl repository = repository();
        repository.saveRuntime(runtime("CP1", "C001"));
        repository.saveRuntime(runtime("CP2", "C002"));

        assertTrue(redis.containsKey("tracking:cp1:process:runtime"));
        assertTrue(redis.containsKey("tracking:cp2:process:runtime"));
        assertEquals("tracking:cp1:shear:runtime",
                RedisKeys.trackingRuntime("CP1", TrackingType.SHEAR));
        assertEquals("C001", repository.findRuntimeAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingRuntime.class
        ).orElseThrow(AssertionError::new).getSegments().get("S1").getCoilNo());
        assertEquals("C002", repository.findRuntimeAs(
                "CP2", TrackingType.PROCESS, ProcessTrackingRuntime.class
        ).orElseThrow(AssertionError::new).getSegments().get("S1").getCoilNo());
    }

    @Test
    void refreshesExistingConfigObjectWithoutReplacingItsReference() {
        TrackingRuntimeRepositoryImpl repository = repository();
        CubeApiGateway cubeApiGateway = mock(CubeApiGateway.class);
        TrackingResultRepositoryDispatcher dispatcher = mock(TrackingResultRepositoryDispatcher.class);
        ReflectionTestUtils.setField(repository, "cubeApiGateway", cubeApiGateway);
        ReflectionTestUtils.setField(repository, "trackingResultRepositoryDispatcher", dispatcher);

        ProcessTrackingConfig initial = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(false)
                .mqttTopic("initial")
                .build();
        ProcessTrackingConfig refreshed = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("refreshed")
                .build();
        when(cubeApiGateway.fetchTrackingConfigs())
                .thenReturn(configs(initial), configs(refreshed));

        repository.refreshConfig();
        TrackingConfig cached = repository.findConfig("CP1", TrackingType.PROCESS)
                .orElseThrow(AssertionError::new);
        repository.refreshConfig();

        TrackingConfig updated = repository.findConfig("CP1", TrackingType.PROCESS)
                .orElseThrow(AssertionError::new);
        assertSame(cached, updated);
        assertTrue(updated.getEnable());
        assertEquals("refreshed", updated.getMqttTopic());
    }

    private TrackingRuntimeRepositoryImpl repository() {
        TrackingRuntimeRepositoryImpl repository = new TrackingRuntimeRepositoryImpl();
        ReflectionTestUtils.setField(repository, "stringRedisTemplate", redisTemplate);
        return repository;
    }

    private ProcessTrackingRuntime runtime(String unitCode, String coilNo) {
        ProcessSegmentRuntime segment = ProcessSegmentRuntime.builder()
                .segmentCode("S1")
                .coilNo(coilNo)
                .headLength(BigDecimal.ONE)
                .build();
        return ProcessTrackingRuntime.builder()
                .unitCode(unitCode)
                .trackingType(TrackingType.PROCESS)
                .updatedAt(Instant.now())
                .segments(Collections.singletonMap("S1", segment))
                .build();
    }

    private Map<TrackingType, TrackingConfig> configs(TrackingConfig config) {
        Map<TrackingType, TrackingConfig> configs = new EnumMap<>(TrackingType.class);
        configs.put(TrackingType.PROCESS, config);
        return configs;
    }
}
