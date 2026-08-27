package com.wisdri.tracking.infrastructure.repository.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.runtime.batch.BatchTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessSegmentRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearDeviceRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime;
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
import java.util.Arrays;
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
    void savesSnakeCaseJsonWithoutRestoringRuntimeIntoNewProcessCache() {
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
                .speedPointValue(new BigDecimal("2.5"))
                .startConditionPointValue(BigDecimal.ONE)
                .segments(Collections.singletonMap("S1", segment))
                .build();

        repository.saveRuntime(runtime);

        String json = redis.get("tracking:cp1:process:runtime");
        assertTrue(json.contains("\"head_length\""));
        assertTrue(json.contains("\"updated_at\" : \"2026-07-05T20:00:00+08:00\""));
        assertTrue(json.contains("\"speed_point_value\" : 2.5"));
        assertTrue(json.contains("\"start_condition_point_value\" : 1"));
        assertFalse(json.contains("template_code"));

        ProcessTrackingRuntime cached = repository.findRuntimeAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingRuntime.class
        ).orElseThrow(AssertionError::new);
        assertEquals("C001", cached.getSegments().get("S1").getCoilNo());
        assertEquals(new BigDecimal("12.30"), cached.getSegments().get("S1").getHeadLength());
        assertEquals(new BigDecimal("2.5"), cached.getSpeedPointValue());
        assertEquals(BigDecimal.ONE, cached.getStartConditionPointValue());
        assertEquals(Instant.parse("2026-07-05T12:00:00Z"), cached.getUpdatedAt());
        assertFalse(repository().findRuntimeAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingRuntime.class).isPresent());
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
        BatchTrackingRuntime cached = repository.findRuntimeAs(
                "BAF1", TrackingType.BATCH, "fb1", BatchTrackingRuntime.class
        ).orElseThrow(AssertionError::new);
        assertEquals("fb1", cached.getTemplateCode());
        assertEquals(new BigDecimal("1"), cached.getProductionStatus());
        assertEquals("N001", cached.getCoilNos().get("north"));
        assertFalse(repository().findRuntimeAs(
                "BAF1", TrackingType.BATCH, "fb1", BatchTrackingRuntime.class).isPresent());
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
    void storesEachShearRuntimeBelowDirectoryByDeviceCode() throws Exception {
        TrackingRuntimeRepositoryImpl repository = repository();
        ShearTrackingRuntime runtime = ShearTrackingRuntime.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.SHEAR)
                .deviceCode("TR-A")
                .uncoiler(ShearDeviceRuntime.builder()
                        .side(DeviceSide.UNCOILER)
                        .running(true)
                        .deviceCode("POR-1")
                        .productNo(1)
                        .head(ShearCounterRuntime.builder().build())
                        .build())
                .coiler(ShearDeviceRuntime.builder()
                        .side(DeviceSide.COILER)
                        .running(true)
                        .deviceCode("TR-A")
                        .productNo(1)
                        .slice(ShearCounterRuntime.builder().build())
                        .tail(ShearCounterRuntime.builder().build())
                        .build())
                .build();

        repository.saveRuntime(runtime);

        String key = "tracking:cp1:shear:runtime:tr-a";
        assertTrue(redis.containsKey(key));
        assertFalse(redis.containsKey("tracking:cp1:shear:runtime"));
        assertEquals(key, RedisKeys.trackingRuntime("CP1", TrackingType.SHEAR, "TR-A"));
        assertTrue(redis.get(key).contains("\"uncoiler\""));
        assertTrue(redis.get(key).contains("\"coiler\""));
        assertTrue(redis.get(key).contains("\"HEAD\""));
        assertTrue(redis.get(key).contains("\"SLICE\""));
        assertTrue(redis.get(key).contains("\"TAIL\""));
        assertFalse(new ObjectMapper().readTree(redis.get(key)).has("device_code"));
        ShearTrackingRuntime cached = repository.findRuntimeAs(
                "CP1", TrackingType.SHEAR, "TR-A", ShearTrackingRuntime.class)
                .orElseThrow(AssertionError::new);
        assertEquals("TR-A", cached.getDeviceCode());
        assertEquals("POR-1", cached.getUncoiler().getDeviceCode());
        assertEquals("TR-A", cached.getCoiler().getDeviceCode());
    }

    @Test
    void savesAndRestoresStatusCandidateWindowsAndCurrentState() {
        TrackingRuntimeRepositoryImpl repository = repository();
        StatusCandidateRuntime candidate = StatusCandidateRuntime.builder()
                .coilNo("C001")
                .productNo(2)
                .coilerMethod("11")
                .coilerMethodName("上开卷")
                .maxLength(new BigDecimal("100"))
                .lengths(Arrays.asList(new BigDecimal("100"), new BigDecimal("95")))
                .build();
        StatusCurrentRuntime current = StatusCurrentRuntime.builder()
                .side(DeviceSide.UNCOILER)
                .running(true)
                .deviceCode("U1")
                .coilerMethod("11")
                .coilerMethodName("上开卷")
                .coilNo("C001")
                .productNo(2)
                .remainingLength(new BigDecimal("95"))
                .maxLength(new BigDecimal("100"))
                .build();
        StatusTrackingRuntime status = StatusTrackingRuntime.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.STATUS)
                .startConditionPointValue(BigDecimal.ONE)
                .candidates(Collections.singletonMap("U1", candidate))
                .current(Collections.singletonMap(DeviceSide.UNCOILER, current))
                .build();

        repository.saveRuntime(status);

        assertTrue(redis.containsKey("tracking:cp1:status:runtime"));
        assertFalse(redis.get("tracking:cp1:status:runtime").contains("updated_at"));
        assertTrue(redis.get("tracking:cp1:status:runtime").contains(
                "\"lengths\" : [ 100, 95 ]"));
        assertTrue(redis.get("tracking:cp1:status:runtime").contains("\"product_no\" : 2"));
        StatusTrackingRuntime cached = repository.findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class
        ).orElseThrow(AssertionError::new);
        assertEquals("C001", cached.getCandidates().get("U1").getCoilNo());
        assertEquals(2, cached.getCandidates().get("U1").getProductNo());
        assertEquals("11", cached.getCandidates().get("U1").getCoilerMethod());
        assertEquals("上开卷", cached.getCandidates().get("U1").getCoilerMethodName());
        assertEquals(new BigDecimal("100"), cached.getCandidates().get("U1").getMaxLength());
        assertEquals(Arrays.asList(new BigDecimal("100"), new BigDecimal("95")),
                cached.getCandidates().get("U1").getLengths());
        assertTrue(cached.getCurrent().get(DeviceSide.UNCOILER).getRunning());
        assertEquals(2, cached.getCurrent().get(DeviceSide.UNCOILER).getProductNo());
        assertEquals("11", cached.getCurrent().get(DeviceSide.UNCOILER).getCoilerMethod());
        assertEquals("上开卷", cached.getCurrent().get(DeviceSide.UNCOILER).getCoilerMethodName());
        assertEquals(new BigDecimal("95"),
                cached.getCurrent().get(DeviceSide.UNCOILER).getRemainingLength());
        assertEquals(new BigDecimal("100"),
                cached.getCurrent().get(DeviceSide.UNCOILER).getMaxLength());
        assertFalse(repository().findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).isPresent());
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
    void synchronizesAllSupportedTrackingConfigs() {
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
        StatusTrackingConfig status = StatusTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.STATUS)
                .enable(true)
                .build();
        Map<TrackingType, TrackingConfig> fetched = new EnumMap<>(TrackingType.class);
        fetched.put(TrackingType.PROCESS, process);
        fetched.put(TrackingType.BATCH, batch);
        fetched.put(TrackingType.STATUS, status);
        when(cubeApiGateway.fetchTrackingConfigs()).thenReturn(fetched);

        repository.refreshConfig();

        assertTrue(redis.containsKey("tracking:baf1:process:config"));
        assertTrue(redis.containsKey("tracking:baf1:batch:config"));
        assertTrue(redis.containsKey("tracking:baf1:status:config"));
        assertTrue(repository.findConfigAs(
                "BAF1", TrackingType.BATCH, BatchTrackingConfig.class
        ).isPresent());
        verify(dispatcher).createTable(process);
        verify(dispatcher).createTable(batch);
        verify(dispatcher).createTable(status);
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
