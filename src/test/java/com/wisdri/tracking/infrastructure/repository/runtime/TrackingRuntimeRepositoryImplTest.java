package com.wisdri.tracking.infrastructure.repository.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.config.process.RollingConfig;
import com.wisdri.tracking.domain.model.runtime.batch.BatchTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessSegmentRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
                .side(DeviceSide.COILER)
                .deviceName("1#卷取机")
                .coilNo("COIL-1")
                .productNo(1)
                .head(ShearCounterRuntime.builder().shearNo(0).cutNo(0).build())
                .slice(ShearCounterRuntime.builder().shearNo(0).cutNo(0).build())
                .tail(ShearCounterRuntime.builder().shearNo(0).cutNo(0).build())
                .build();

        repository.saveRuntime(runtime);

        String key = "tracking:cp1:shear:runtime:tr-a";
        assertTrue(redis.containsKey(key));
        assertFalse(redis.containsKey("tracking:cp1:shear:runtime"));
        assertEquals(key, RedisKeys.trackingRuntime("CP1", TrackingType.SHEAR, "TR-A"));
        assertFalse(redis.get(key).contains("\"uncoiler\""));
        assertTrue(redis.get(key).contains("\"side\" : \"coiler\""));
        assertTrue(redis.get(key).contains("\"HEAD\""));
        assertTrue(redis.get(key).contains("\"SLICE\""));
        assertTrue(redis.get(key).contains("\"TAIL\""));
        assertEquals("TR-A", new ObjectMapper().readTree(redis.get(key)).get("device_code").asText());
        ShearTrackingRuntime cached = repository.findRuntimeAs(
                "CP1", TrackingType.SHEAR, "TR-A", ShearTrackingRuntime.class)
                .orElseThrow(AssertionError::new);
        assertEquals("TR-A", cached.getDeviceCode());
        assertEquals("COIL-1", cached.getCoilNo());
        assertEquals(DeviceSide.COILER, cached.getSide());
    }

    @Test
    void validatesWholeRuntimeBatchBeforeWritingRedis() {
        TrackingRuntimeRepositoryImpl repository = repository();
        ShearTrackingRuntime valid = ShearTrackingRuntime.builder()
                .unitCode("CP1").trackingType(TrackingType.SHEAR).deviceCode("TR-A").build();
        ShearTrackingRuntime invalid = ShearTrackingRuntime.builder()
                .unitCode("CP1").trackingType(TrackingType.SHEAR).build();

        assertThrows(RuntimeException.class, () -> repository.saveRuntimes(Arrays.asList(valid, invalid)));
        assertTrue(redis.isEmpty());
        assertFalse(repository.findRuntimeAs(
                "CP1", TrackingType.SHEAR, "TR-A", ShearTrackingRuntime.class).isPresent());
    }

    @Test
    void keepsWholeLocalRuntimeBatchWhenRedisWriteFails() {
        TrackingRuntimeRepositoryImpl repository = repository();
        ShearTrackingRuntime runtime = ShearTrackingRuntime.builder()
                .unitCode("CP1").trackingType(TrackingType.SHEAR).deviceCode("TR-A").build();
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations).set(
                        org.mockito.ArgumentMatchers.eq("tracking:cp1:shear:runtime:tr-a"), anyString());

        assertThrows(RuntimeException.class, () -> repository.saveRuntimes(Arrays.asList(runtime)));
        assertTrue(repository.findRuntimeAs(
                "CP1", TrackingType.SHEAR, "TR-A", ShearTrackingRuntime.class).isPresent());
    }

    @Test
    void savesAndRestoresStatusCandidateWindowsAndCurrentState() {
        TrackingRuntimeRepositoryImpl repository = repository();
        StatusCandidateRuntime candidate = StatusCandidateRuntime.builder()
                .deviceCode("U1")
                .deviceName("1#开卷机")
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
                .nullCount(3)
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
        assertTrue(redis.get("tracking:cp1:status:runtime").contains("\"device_code\" : \"U1\""));
        assertTrue(redis.get("tracking:cp1:status:runtime").contains("\"device_name\" : \"1#开卷机\""));
        assertTrue(redis.get("tracking:cp1:status:runtime").contains("\"null_count\" : 3"));
        StatusTrackingRuntime cached = repository.findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class
        ).orElseThrow(AssertionError::new);
        assertEquals("U1", cached.getCandidates().get("U1").getDeviceCode());
        assertEquals("1#开卷机", cached.getCandidates().get("U1").getDeviceName());
        assertEquals("C001", cached.getCandidates().get("U1").getCoilNo());
        assertEquals(2, cached.getCandidates().get("U1").getProductNo());
        assertEquals("11", cached.getCandidates().get("U1").getCoilerMethod());
        assertEquals("上开卷", cached.getCandidates().get("U1").getCoilerMethodName());
        assertEquals(new BigDecimal("100"), cached.getCandidates().get("U1").getMaxLength());
        assertEquals(Arrays.asList(new BigDecimal("100"), new BigDecimal("95")),
                cached.getCandidates().get("U1").getLengths());
        assertTrue(cached.getCurrent().get(DeviceSide.UNCOILER).getRunning());
        assertEquals(3, cached.getCurrent().get(DeviceSide.UNCOILER).getNullCount());
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
    void restoresStatusFromRedisAtOneMinuteBoundary() {
        Instant now = Instant.parse("2026-09-24T06:00:00Z");
        StatusTrackingRuntime status = StatusTrackingRuntime.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.STATUS)
                .receivedAt(now.minusSeconds(60))
                .rollingDirection(false)
                .rollingDirectReverse(true)
                .passNo(2)
                .candidates(Collections.singletonMap("TR1", StatusCandidateRuntime.builder()
                        .deviceCode("TR1")
                        .coilNo("C001")
                        .maxLength(new BigDecimal("100"))
                        .lengths(Arrays.asList(new BigDecimal("100"), new BigDecimal("95")))
                        .build()))
                .current(Collections.singletonMap(DeviceSide.UNCOILER, StatusCurrentRuntime.builder()
                        .side(DeviceSide.UNCOILER)
                        .deviceCode("TR1")
                        .coilNo("C001")
                        .remainingLength(new BigDecimal("95"))
                        .maxLength(new BigDecimal("100"))
                        .build()))
                .build();
        repository().saveRuntime(status);
        TrackingRuntimeRepositoryImpl restarted = repository();
        ReflectionTestUtils.setField(restarted, "clock", Clock.fixed(now, ZoneOffset.UTC));

        StatusTrackingRuntime restored = restarted.findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).orElseThrow(AssertionError::new);

        assertEquals(now.minusSeconds(60), restored.getReceivedAt());
        assertEquals(false, restored.getRollingDirection());
        assertEquals(true, restored.getRollingDirectReverse());
        assertEquals(2, restored.getPassNo());
        assertEquals(Arrays.asList(new BigDecimal("100"), new BigDecimal("95")),
                restored.getCandidates().get("TR1").getLengths());
        assertEquals(new BigDecimal("95"),
                restored.getCurrent().get(DeviceSide.UNCOILER).getRemainingLength());
        assertSame(restored, restarted.findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).orElseThrow(AssertionError::new));
        verify(redisTemplate.opsForValue(), times(1)).get("tracking:cp1:status:runtime");
    }

    @Test
    void ignoresExpiredFutureMissingAndMalformedStatusRuntime() {
        Instant now = Instant.parse("2026-09-24T06:00:00Z");
        String key = "tracking:cp1:status:runtime";
        for (Instant receivedAt : Arrays.asList(now.minusSeconds(61), now.plusSeconds(1))) {
            repository().saveRuntime(StatusTrackingRuntime.builder()
                    .unitCode("CP1").trackingType(TrackingType.STATUS)
                    .receivedAt(receivedAt).build());
            TrackingRuntimeRepositoryImpl restarted = restartedAt(now);
            assertFalse(restarted.findRuntimeAs(
                    "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).isPresent());
            StatusTrackingRuntime fresh = StatusTrackingRuntime.builder()
                    .unitCode("CP1").trackingType(TrackingType.STATUS).receivedAt(now).build();
            restarted.saveRuntime(fresh);
            assertSame(fresh, restarted.findRuntimeAs(
                    "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).orElseThrow(AssertionError::new));
        }
        repository().saveRuntime(StatusTrackingRuntime.builder()
                .unitCode("CP1").trackingType(TrackingType.STATUS).build());
        assertFalse(restartedAt(now).findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).isPresent());

        redis.put(key, "{invalid json");
        assertFalse(restartedAt(now).findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).isPresent());
    }

    @Test
    void ignoresRecentStatusWhenDeviceListOrDirectionConfigChanged() {
        Instant now = Instant.parse("2026-09-24T06:00:00Z");
        repository().saveRuntime(StatusTrackingRuntime.builder()
                .unitCode("CP1").trackingType(TrackingType.STATUS)
                .receivedAt(now.minusSeconds(10))
                .rollingDirectReverse(false)
                .candidates(Collections.singletonMap("TR1", StatusCandidateRuntime.builder()
                        .deviceCode("TR1").build()))
                .build());

        StatusTrackingConfig changedDevices = StatusTrackingConfig.builder()
                .unitCode("CP1").trackingType(TrackingType.STATUS)
                .tracking(StatusTrackingSection.builder()
                        .rolling(RollingConfig.builder().directReverse(false).build())
                        .points(Arrays.asList(StatusPointGroup.builder().code("TR1").build(),
                                StatusPointGroup.builder().code("TR2").build()))
                        .build())
                .build();
        assertFalse(restartedWithConfig(now, changedDevices).findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).isPresent());

        StatusTrackingConfig changedDirection = StatusTrackingConfig.builder()
                .unitCode("CP1").trackingType(TrackingType.STATUS)
                .tracking(StatusTrackingSection.builder()
                        .rolling(RollingConfig.builder().directReverse(true).build())
                        .points(Collections.singletonList(StatusPointGroup.builder().code("TR1").build()))
                        .build())
                .build();
        assertFalse(restartedWithConfig(now, changedDirection).findRuntimeAs(
                "CP1", TrackingType.STATUS, StatusTrackingRuntime.class).isPresent());
    }

    private TrackingRuntimeRepositoryImpl restartedWithConfig(Instant now, StatusTrackingConfig config) {
        TrackingRuntimeRepositoryImpl restarted = restartedAt(now);
        CubeApiGateway gateway = mock(CubeApiGateway.class);
        ReflectionTestUtils.setField(restarted, "cubeApiGateway", gateway);
        ReflectionTestUtils.setField(restarted, "trackingResultRepositoryDispatcher",
                mock(TrackingResultRepositoryDispatcher.class));
        when(gateway.fetchTrackingConfigs()).thenReturn(
                Collections.<TrackingType, TrackingConfig>singletonMap(TrackingType.STATUS, config));
        restarted.refreshConfig();
        return restarted;
    }

    private TrackingRuntimeRepositoryImpl restartedAt(Instant now) {
        TrackingRuntimeRepositoryImpl restarted = repository();
        ReflectionTestUtils.setField(restarted, "clock", Clock.fixed(now, ZoneOffset.UTC));
        return restarted;
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
