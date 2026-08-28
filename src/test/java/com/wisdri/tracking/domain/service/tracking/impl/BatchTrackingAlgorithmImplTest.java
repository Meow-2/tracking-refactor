package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.SegmentConfig;
import com.wisdri.tracking.domain.model.config.batch.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.batch.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.batch.BatchTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.batch.BatchResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchTrackingAlgorithmImplTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-07-17T08:00:00Z");

    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;
    private PointEventHandlerDispatcher pointEventHandlerDispatcher;
    private TrackingStepLogger trackingStepLogger;
    private BatchTrackingAlgorithmImpl algorithm;

    @BeforeEach
    void setUp() {
        runtimeRepositoryDispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        pointEventHandlerDispatcher = mock(PointEventHandlerDispatcher.class);
        trackingStepLogger = mock(TrackingStepLogger.class);
        algorithm = new BatchTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", runtimeRepositoryDispatcher);
        ReflectionTestUtils.setField(algorithm, "pointEventHandlerDispatcher", pointEventHandlerDispatcher);
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", trackingStepLogger);
        when(runtimeRepositoryDispatcher.findConfigAs(
                "BAF1", TrackingType.BATCH, BatchTrackingConfig.class
        )).thenReturn(Optional.of(config()));
        doNothing().when(runtimeRepositoryDispatcher).saveRuntime(any(TrackingRuntime.class));
    }

    @Test
    void generatesBothSidesAndMergesCommonParameters() {
        Map<String, Object> values = baseValues(new BigDecimal("1"), "N001", "S001");
        values.put("common/fb1_shared", "common");
        values.put("common/fb1_common_only", 10);
        values.put("north/fb1_shared", "north");
        values.put("north/fb1_north_only", 20);
        values.put("south/fb1_shared", "south");
        values.put("south/fb1_south_only", null);

        List<BatchResult> results = algorithm.calculate(input(values));

        assertEquals(2, results.size());
        BatchResult north = results.get(0);
        assertEquals("fb1", north.getTemplateCode());
        assertEquals("north", north.getSegmentCode());
        assertEquals("N001", north.getCoilNo());
        assertEquals("north", north.getParameters().get("shared"));
        assertEquals(10, north.getParameters().get("common_only"));
        assertEquals(20, north.getParameters().get("north_only"));
        assertEquals(RECEIVED_AT, north.getReceivedAt());
        verify(pointEventHandlerDispatcher).handle(any(TrackingInput.class), any(BatchTrackingConfig.class));
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("生产条件检查"), anyMap());
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("区段结果生成"), eq("north"), anyMap());

        BatchResult south = results.get(1);
        assertEquals("south", south.getSegmentCode());
        assertEquals("south", south.getParameters().get("shared"));
        assertFalse(south.getParameters().containsKey("south_only"));
    }

    @Test
    void skipsEmptySideAndTrimsInvisibleCoilCharacters() {
        Map<String, Object> values = baseValues(
                new BigDecimal("1"), "\u200B \u0000N001\u0007 \uFEFF", "\u200B \u0000\uFEFF");

        List<BatchResult> results = algorithm.calculate(input(values));

        assertEquals(1, results.size());
        assertEquals("north", results.get(0).getSegmentCode());
        assertEquals("N001", results.get(0).getCoilNo());
        BatchTrackingRuntime runtime = savedRuntime();
        assertEquals("N001", runtime.getCoilNos().get("north"));
        assertFalse(runtime.getCoilNos().containsKey("south"));
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("区段跳过"), eq("south"), anyMap());
    }

    @Test
    void updatesRuntimeWithoutResultWhenProductionStatusIsInvalidOrBelowThreshold() {
        List<BatchResult> below = algorithm.calculate(input(baseValues(
                new BigDecimal("-1"), "N001", "S001")));
        List<BatchResult> invalid = algorithm.calculate(input(baseValues(
                "not-number", "N002", "S002")));
        Map<String, Object> missingStatusValues = baseValues(null, "N003", "S003");
        missingStatusValues.remove("tracking/fb1_prod_status");
        List<BatchResult> missing = algorithm.calculate(input(missingStatusValues));

        assertTrue(below.isEmpty());
        assertTrue(invalid.isEmpty());
        assertTrue(missing.isEmpty());
        ArgumentCaptor<TrackingRuntime> captor = ArgumentCaptor.forClass(TrackingRuntime.class);
        verify(runtimeRepositoryDispatcher, times(3)).saveRuntime(captor.capture());
        BatchTrackingRuntime belowRuntime = (BatchTrackingRuntime) captor.getAllValues().get(0);
        assertEquals(new BigDecimal("-1"), belowRuntime.getProductionStatus());
        assertEquals("N001", belowRuntime.getCoilNos().get("north"));
        BatchTrackingRuntime invalidRuntime = (BatchTrackingRuntime) captor.getAllValues().get(1);
        assertNull(invalidRuntime.getProductionStatus());
        assertEquals("N002", invalidRuntime.getCoilNos().get("north"));
        BatchTrackingRuntime missingRuntime = (BatchTrackingRuntime) captor.getAllValues().get(2);
        assertNull(missingRuntime.getProductionStatus());
        assertEquals("N003", missingRuntime.getCoilNos().get("north"));
    }

    @Test
    void writesEveryFrameEvenWhenCoilNumberDoesNotChange() {
        TrackingInput input = input(baseValues(new BigDecimal("1"), "N001", null));

        assertEquals(1, algorithm.calculate(input).size());
        assertEquals(1, algorithm.calculate(input).size());
        verify(runtimeRepositoryDispatcher, times(2)).saveRuntime(any(TrackingRuntime.class));
    }

    @Test
    void requiresTemplateCode() {
        TrackingInput input = input(Collections.emptyMap());
        input.setTemplateCode(null);

        assertThrows(IllegalArgumentException.class, () -> algorithm.calculate(input));
    }

    @Test
    void supportsOnlyBatchTracking() {
        assertTrue(algorithm.support(TrackingType.BATCH));
        assertFalse(algorithm.support(TrackingType.PROCESS));
    }

    private BatchTrackingRuntime savedRuntime() {
        ArgumentCaptor<TrackingRuntime> captor = ArgumentCaptor.forClass(TrackingRuntime.class);
        verify(runtimeRepositoryDispatcher).saveRuntime(captor.capture());
        return (BatchTrackingRuntime) captor.getValue();
    }

    private BatchTrackingConfig config() {
        TrackingSection tracking = TrackingSection.builder()
                .pointPrefix("tracking/{template}_")
                .startCondition(StartCondition.builder()
                        .point(point("prod_status"))
                        .threshold(BigDecimal.ZERO)
                        .build())
                .points(Arrays.asList(
                        TrackingPointGroup.builder().segment("north").coilNo(point("north_coil_no")).build(),
                        TrackingPointGroup.builder().segment("south").coilNo(point("south_coil_no")).build()
                ))
                .build();
        SegmentConfig common = SegmentConfig.builder()
                .code("common")
                .name("公共")
                .pointPrefix("common/{template}_")
                .points(Arrays.asList(point("shared"), point("common_only")))
                .build();
        SegmentConfig north = SegmentConfig.builder()
                .code("north")
                .name("北侧")
                .pointPrefix("north/{template}_")
                .points(Arrays.asList(point("shared"), point("north_only")))
                .build();
        SegmentConfig south = SegmentConfig.builder()
                .code("south")
                .name("南侧")
                .pointPrefix("south/{template}_")
                .points(Arrays.asList(point("shared"), point("south_only")))
                .build();
        return BatchTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .tracking(tracking)
                .segments(Arrays.asList(south, north, common))
                .build();
    }

    private PointConfig point(String name) {
        return PointConfig.builder().name(name).build();
    }

    private TrackingInput input(Map<String, Object> values) {
        return TrackingInput.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .templateCode("fb1")
                .latestSnapshot(PointSnapshot.builder()
                        .values(values)
                        .receivedAt(RECEIVED_AT)
                        .build())
                .build();
    }

    private Map<String, Object> baseValues(Object productionStatus, String northCoilNo, String southCoilNo) {
        Map<String, Object> values = new HashMap<>();
        values.put("tracking/fb1_prod_status", productionStatus);
        values.put("tracking/fb1_north_coil_no", northCoilNo);
        values.put("tracking/fb1_south_coil_no", southCoilNo);
        return values;
    }
}
