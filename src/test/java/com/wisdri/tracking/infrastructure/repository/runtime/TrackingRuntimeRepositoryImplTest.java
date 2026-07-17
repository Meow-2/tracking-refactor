package com.wisdri.tracking.infrastructure.repository.runtime;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.runtime.batch.BatchTrackingRuntime;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        assertFalse(json.contains("template_code"));

        TrackingRuntimeRepositoryImpl restartedRepository = repository();
        ProcessTrackingRuntime restored = restartedRepository.findRuntimeAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingRuntime.class
        ).orElseThrow(AssertionError::new);
        assertEquals("C001", restored.getSegments().get("S1").getCoilNo());
        assertEquals(new BigDecimal("12.30"), restored.getSegments().get("S1").getHeadLength());
    }

    @Test
    void isolatesBatchRuntimeByTemplateCode() {
        TrackingRuntimeRepositoryImpl repository = repository();
        BatchTrackingRuntime fb1 = batchRuntime("fb1", "N001");
        BatchTrackingRuntime fb2 = batchRuntime("fb2", "N002");

        repository.saveRuntime(fb1);
        repository.saveRuntime(fb2);

        assertTrue(redis.containsKey("tracking:baf1:batch:fb1:runtime"));
        assertTrue(redis.containsKey("tracking:baf1:batch:fb2:runtime"));
        TrackingRuntimeRepositoryImpl restartedRepository = repository();
        BatchTrackingRuntime restored = restartedRepository.findRuntimeAs(
                "BAF1", TrackingType.BATCH, "fb1", BatchTrackingRuntime.class
        ).orElseThrow(AssertionError::new);
        assertEquals("fb1", restored.getTemplateCode());
        assertEquals(new BigDecimal("1"), restored.getProductionStatus());
        assertEquals("N001", restored.getCoilNos().get("north"));
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

    @Test
    void synchronizesProcessAndBatchConfigsAfterBatchSupportIsActivated() {
        TrackingRuntimeRepositoryImpl repository = repository();
        CubeApiGateway cubeApiGateway = mock(CubeApiGateway.class);
        TrackingResultRepositoryDispatcher dispatcher = mock(TrackingResultRepositoryDispatcher.class);
        ReflectionTestUtils.setField(repository, "cubeApiGateway", cubeApiGateway);
        ReflectionTestUtils.setField(repository, "trackingResultRepositoryDispatcher", dispatcher);

        ProcessTrackingConfig process = ProcessTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .build();
        BatchTrackingConfig batch = BatchTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .enable(true)
                .build();
        Map<TrackingType, TrackingConfig> fetched = new EnumMap<>(TrackingType.class);
        fetched.put(TrackingType.PROCESS, process);
        fetched.put(TrackingType.BATCH, batch);
        when(cubeApiGateway.fetchTrackingConfigs()).thenReturn(fetched);

        repository.refreshConfig();

        assertTrue(redis.containsKey("tracking:baf1:process:config"));
        assertTrue(redis.containsKey("tracking:baf1:batch:config"));
        assertTrue(repository.findConfigAs(
                "BAF1", TrackingType.BATCH, BatchTrackingConfig.class
        ).isPresent());
        verify(dispatcher).createTable(process);
        verify(dispatcher).createTable(batch);
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

    private BatchTrackingRuntime batchRuntime(String templateCode, String coilNo) {
        return BatchTrackingRuntime.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .templateCode(templateCode)
                .productionStatus(BigDecimal.ONE)
                .coilNos(Collections.singletonMap("north", coilNo))
                .updatedAt(Instant.now())
                .build();
    }

    private Map<TrackingType, TrackingConfig> configs(TrackingConfig config) {
        Map<TrackingType, TrackingConfig> configs = new EnumMap<>(TrackingType.class);
        configs.put(TrackingType.PROCESS, config);
        return configs;
    }
}
