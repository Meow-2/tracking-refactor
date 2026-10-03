package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.process.LengthMode;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.RollingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.process.TrackingSection;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessTrackingAlgorithmImplTest {
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;
    private TrackingStepLogger trackingStepLogger;
    private ProcessTrackingAlgorithmImpl algorithm;

    @BeforeEach
    void setUp() {
        runtimeRepositoryDispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        trackingStepLogger = mock(TrackingStepLogger.class);
        algorithm = new ProcessTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", runtimeRepositoryDispatcher);
        ReflectionTestUtils.setField(algorithm, "abnormalDataHandlerDispatcher",
                mock(AbnormalDataHandlerDispatcher.class));
        ReflectionTestUtils.setField(algorithm, "pointEventHandlerDispatcher",
                mock(PointEventHandlerDispatcher.class));
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", trackingStepLogger);
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config()));
        doNothing().when(runtimeRepositoryDispatcher).saveRuntime(any(TrackingRuntime.class));
    }

    @Test
    void replacesSegmentRuntimeWithCurrentCalculationIncludingEmptyState() {
        List<ProcessResult> results = algorithm.calculate(input(values("C001", new BigDecimal("15.5"))));
        assertEquals(1, results.size());
        assertEquals("C001", results.get(0).getCoilNo());
        assertEquals(Integer.valueOf(2), results.get(0).getRepeatProdNo());
        assertEquals(new BigDecimal("15.5"), results.get(0).getHeadLength());

        List<ProcessResult> emptyResults = algorithm.calculate(input(Collections.emptyMap()));
        assertTrue(emptyResults.isEmpty());

        ArgumentCaptor<TrackingRuntime> captor = ArgumentCaptor.forClass(TrackingRuntime.class);
        verify(runtimeRepositoryDispatcher, times(2)).saveRuntime(captor.capture());
        ProcessTrackingRuntime populated = (ProcessTrackingRuntime) captor.getAllValues().get(0);
        assertEquals("C001", populated.getSegments().get("S1").getCoilNo());
        assertEquals(new BigDecimal("15.5"), populated.getSegments().get("S1").getHeadLength());
        assertEquals(new BigDecimal("2.5"), populated.getSpeedPointValue());
        assertEquals(BigDecimal.ONE, populated.getStartConditionPointValue());
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("状态物料检查"), eq("S1"), anyMap());
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("区段结果生成"), eq("S1"), anyMap());

        ProcessTrackingRuntime cleared = (ProcessTrackingRuntime) captor.getAllValues().get(1);
        assertTrue(cleared.getSegments().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void logsFailedStartConditionAndSkipsCalculation() {
        ProcessTrackingConfig config = config();
        config.getTracking().setStartCondition(StartCondition.builder()
                .point(PointConfig.builder().name("status").build())
                .threshold(BigDecimal.ONE)
                .build());
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));
        Map<String, Object> values = values("C001", new BigDecimal("15.5"));
        values.put("status", BigDecimal.ZERO);

        assertTrue(algorithm.calculate(input(values)).isEmpty());

        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("启动条件检查"), details.capture());
        assertEquals(false, details.getValue().get("passed"));
        assertEquals(BigDecimal.ZERO, details.getValue().get("actual"));

        ArgumentCaptor<TrackingRuntime> runtime = ArgumentCaptor.forClass(TrackingRuntime.class);
        verify(runtimeRepositoryDispatcher).saveRuntime(runtime.capture());
        ProcessTrackingRuntime saved = (ProcessTrackingRuntime) runtime.getValue();
        assertEquals(new BigDecimal("2.5"), saved.getSpeedPointValue());
        assertEquals(BigDecimal.ZERO, saved.getStartConditionPointValue());
        assertTrue(saved.getSegments().isEmpty());
    }

    @Test
    void rollingUsesStatusMaterialAndKeepsPassNumber() {
        ProcessTrackingConfig config = config();
        config.getTracking().setLengthMode(LengthMode.ROLLING);
        config.getTracking().setRolling(RollingConfig.builder()
                .directPoint(PointConfig.builder().name("direction").build())
                .passNoPoint(PointConfig.builder().name("pass").build())
                .directReverse(false)
                .build());
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));
        Map<String, Object> values = values("POINT-COIL", new BigDecimal("999"));
        values.put("direction", true);
        values.put("pass", 3);

        StatusTrackingContext context = status("STATUS-COIL", 7, "120", "20", "5");
        context.setPassNo(3);
        context.setRollingDirection(true);
        context.setRollingDirectReverse(false);
        List<ProcessResult> results = algorithm.calculate(input(values, context));

        assertEquals(1, results.size());
        assertEquals("STATUS-COIL", results.get(0).getCoilNo());
        assertEquals(Integer.valueOf(7), results.get(0).getRepeatProdNo());
        assertEquals(new BigDecimal("100"), results.get(0).getHeadLength());
        assertEquals(Integer.valueOf(3), results.get(0).getPassNo());
    }

    @Test
    void rollingWaitsForMatchingStatusPassAndDirection() {
        ProcessTrackingConfig config = config();
        config.getTracking().setLengthMode(LengthMode.ROLLING);
        config.getTracking().setRolling(RollingConfig.builder()
                .directPoint(PointConfig.builder().name("direction").build())
                .passNoPoint(PointConfig.builder().name("pass").build())
                .build());
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));
        Map<String, Object> values = values("POINT-COIL", BigDecimal.ONE);
        values.put("pass", 2);
        values.put("direction", true);
        StatusTrackingContext context = status("OLD-COIL", 1, "100", "80", "10");
        context.setPassNo(1);
        context.setRollingDirection(false);
        context.setRollingDirectReverse(false);

        assertTrue(algorithm.calculate(input(values, context)).isEmpty());
        context.setPassNo(2);
        assertTrue(algorithm.calculate(input(values, context)).isEmpty());
        context.setRollingDirection(true);
        assertEquals(1, algorithm.calculate(input(values, context)).size());
    }

    @Test
    void rollingFirstPassHonorsDirectReverse() {
        ProcessTrackingConfig config = config();
        config.getTracking().setLengthMode(LengthMode.ROLLING);
        config.getTracking().setRolling(RollingConfig.builder()
                .directPoint(PointConfig.builder().name("direction").build())
                .passNoPoint(PointConfig.builder().name("pass").build())
                .directReverse(true)
                .build());
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));
        Map<String, Object> values = values("POINT-COIL", BigDecimal.ONE);
        values.put("pass", 1);
        values.put("direction", true);
        StatusTrackingContext context = status("POR-COIL", 4, "100", "80", "10");
        context.setPassNo(1);
        context.setRollingDirection(true);
        context.setRollingDirectReverse(true);

        assertEquals("POR-COIL", algorithm.calculate(input(values, context)).get(0).getCoilNo());
        values.put("direction", false);
        context.setRollingDirection(false);
        assertEquals(1, algorithm.calculate(input(values, context)).size());
        values.put("pass", 2);
        context.setPassNo(2);
        assertEquals(1, algorithm.calculate(input(values, context)).size());
        context.setRollingDirectReverse(false);
        assertTrue(algorithm.calculate(input(values, context)).isEmpty());
    }

    @Test
    void appliesEachSegmentCorrectionToStatusMaterial() {
        ProcessTrackingConfig config = config();
        config.setSegments(java.util.Arrays.asList(
                SegmentConfig.builder().code("S1").lengthCorrect(new BigDecimal("1.5")).build(),
                SegmentConfig.builder().code("S2").lengthCorrect(new BigDecimal("-2")).build()));
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));

        List<ProcessResult> results = algorithm.calculate(input(values("POINT-COIL", new BigDecimal("999")),
                status("STATUS-COIL", null, "100", "75", "2")));

        assertEquals(2, results.size());
        assertEquals(new BigDecimal("26.5"), results.get(0).getHeadLength());
        assertEquals(new BigDecimal("23"), results.get(1).getHeadLength());
        assertEquals(null, results.get(0).getRepeatProdNo());
    }

    @Test
    void formatsFixedAndDefaultCellCodeForEachSegment() {
        ProcessTrackingConfig config = config();
        config.setUnitCode("cp1");
        config.setSegments(java.util.Arrays.asList(
                SegmentConfig.builder().code("S1").cellCodeValue(7).build(),
                SegmentConfig.builder().code("S2").build()));
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));

        List<ProcessResult> results = algorithm.calculate(input(values("C001", BigDecimal.ONE)));

        assertEquals(2, results.size());
        assertEquals("CP1007", results.get(0).getCellCode());
        assertEquals("CP1001", results.get(1).getCellCode());
    }

    @Test
    void readsCellCodePointAndKeepsResultWhenPointValueIsInvalid() {
        ProcessTrackingConfig config = config();
        config.getSegments().get(0).setPointPrefix("/tech/");
        config.getSegments().get(0).setCellCodePoint(PointConfig.builder().name("cell").build());
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));
        Map<String, Object> values = values("C001", BigDecimal.ONE);

        values.put("/tech/cell", 23);
        assertEquals("CP1023", algorithm.calculate(input(values)).get(0).getCellCode());
        values.put("/tech/cell", 0);
        assertEquals("CP1000", algorithm.calculate(input(values)).get(0).getCellCode());
        values.put("/tech/cell", 999);
        assertEquals("CP1999", algorithm.calculate(input(values)).get(0).getCellCode());
        for (Object invalid : new Object[]{null, "1.5", -1, 1000, "unknown"}) {
            values.put("/tech/cell", invalid);
            List<ProcessResult> results = algorithm.calculate(input(values));
            assertEquals(1, results.size());
            assertEquals(null, results.get(0).getCellCode());
            assertEquals("C001", results.get(0).getCoilNo());
        }
    }

    @Test
    void skipsStatusModesWhenCoilerNotStartedOrUncoilerDataIsIncomplete() {
        assertTrue(algorithm.calculate(input(values("C001", BigDecimal.ONE),
                status("C001", 2, "100", "90", null))).isEmpty());
        assertTrue(algorithm.calculate(input(values("C001", BigDecimal.ONE),
                status("C001", 2, "100", "90", "0"))).isEmpty());
        assertTrue(algorithm.calculate(input(values("C001", BigDecimal.ONE),
                status("C001", 2, "100", "90", "-1"))).isEmpty());
        assertTrue(algorithm.calculate(input(values("C001", BigDecimal.ONE),
                status("C001", 2, null, "90", "1"))).isEmpty());
        assertTrue(algorithm.calculate(input(values("C001", BigDecimal.ONE),
                status("C001", 2, "100", null, "1"))).isEmpty());
        assertTrue(algorithm.calculate(input(values("C001", BigDecimal.ONE), null)).isEmpty());
    }

    @Test
    void welderFindsProductNumberFromCurrentBeforeCandidates() {
        ProcessTrackingConfig config = config();
        config.getTracking().setLengthMode(LengthMode.WELDER);
        config.getSegments().get(0).setLengthArrayIndex(0);
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));
        StatusTrackingContext context = status("OTHER", 8, "100", "90", "1");
        context.getCurrent().get(DeviceSide.COILER).setCoilNo("C001");
        context.getCurrent().get(DeviceSide.COILER).setRepeatProdNo(null);
        context.getCandidates().put("candidate", StatusCandidateRuntime.builder()
                .coilNo("C001").repeatProdNo(9).build());

        List<ProcessResult> currentMatched = algorithm.calculate(input(values("C001", new BigDecimal("12")), context));

        assertEquals(1, currentMatched.size());
        assertEquals(null, currentMatched.get(0).getRepeatProdNo());

        context.getCurrent().get(DeviceSide.COILER).setCoilNo("OTHER-2");
        List<ProcessResult> candidateMatched = algorithm.calculate(input(values("C001", new BigDecimal("12")), context));
        assertEquals(Integer.valueOf(9), candidateMatched.get(0).getRepeatProdNo());

        context.getCandidates().clear();
        List<ProcessResult> notMatched = algorithm.calculate(input(values("C001", new BigDecimal("12")), context));
        assertEquals(null, notMatched.get(0).getRepeatProdNo());
    }

    private ProcessTrackingConfig config() {
        PointConfig coilPoint = PointConfig.builder().name("coil").build();
        PointConfig lengthPoint = PointConfig.builder().name("length").build();
        TrackingPointGroup group = TrackingPointGroup.builder()
                .coilNo(coilPoint)
                .length(Collections.singletonList(lengthPoint))
                .build();
        TrackingSection tracking = TrackingSection.builder()
                .speedPoint(PointConfig.builder().name("speed").build())
                .startCondition(StartCondition.builder()
                        .point(PointConfig.builder().name("status").build())
                        .threshold(BigDecimal.ONE)
                        .build())
                .lengthMode(LengthMode.COILER)
                .points(Collections.singletonList(group))
                .build();
        SegmentConfig segment = SegmentConfig.builder()
                .code("S1")
                .name("Segment 1")
                .lengthCorrect(BigDecimal.ZERO)
                .build();
        return ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .tracking(tracking)
                .segments(Collections.singletonList(segment))
                .build();
    }

    private TrackingInput input(Map<String, Object> values) {
        return input(values, status("C001", 2, "100", "84.5", "10"));
    }

    private TrackingInput input(Map<String, Object> values, StatusTrackingContext statusContext) {
        return TrackingInput.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .latestSnapshot(PointSnapshot.builder()
                        .values(values)
                        .receivedAt(Instant.now())
                        .build())
                .statusContext(statusContext)
                .build();
    }

    private StatusTrackingContext status(String coilNo,
                                         Integer repeatProdNo,
                                         String maxLength,
                                         String uncoilerRemainingLength,
                                         String coilerRemainingLength) {
        Map<DeviceSide, StatusCurrentRuntime> current = new LinkedHashMap<>();
        current.put(DeviceSide.UNCOILER, StatusCurrentRuntime.builder()
                .side(DeviceSide.UNCOILER)
                .coilNo(coilNo)
                .repeatProdNo(repeatProdNo)
                .maxLength(decimal(maxLength))
                .remainingLength(decimal(uncoilerRemainingLength))
                .build());
        current.put(DeviceSide.COILER, StatusCurrentRuntime.builder()
                .side(DeviceSide.COILER)
                .remainingLength(decimal(coilerRemainingLength))
                .build());
        return StatusTrackingContext.builder()
                .current(current)
                .candidates(new LinkedHashMap<>())
                .build();
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private Map<String, Object> values(String coilNo, BigDecimal length) {
        Map<String, Object> values = new HashMap<>();
        values.put("coil", coilNo);
        values.put("length", length);
        values.put("speed", new BigDecimal("2.5"));
        values.put("status", BigDecimal.ONE);
        return values;
    }
}
