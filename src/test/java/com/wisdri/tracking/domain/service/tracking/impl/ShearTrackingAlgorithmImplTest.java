package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.shear.CutSetting;
import com.wisdri.tracking.domain.model.config.shear.GratingPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearMode;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearSettings;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.config.shear.ShearTypeCodes;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearDeviceSnapshot;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import com.wisdri.tracking.domain.model.tracking.shear.ShearResult;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import com.wisdri.tracking.domain.service.tracking.shear.ShearDecisionService;
import com.wisdri.tracking.domain.service.tracking.shear.ShearDeviceResolver;
import com.wisdri.tracking.domain.service.tracking.shear.ShearRuntimeService;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 剪切算法 3.0 的触发、刀次、幂等和 runtime 提交回归测试。 */
class ShearTrackingAlgorithmImplTest {
    private static final String UNIT = "LINE-X";
    private static final Instant RECEIVED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private final Map<String, ShearTrackingRuntime> runtimes = new LinkedHashMap<>();
    private TrackingRuntimeRepositoryDispatcher repository;
    private ShearTrackingAlgorithmImpl algorithm;
    private TrackingProperties properties;
    private ShearTrackingConfig config;
    private int frameNumber;

    @BeforeEach
    void setUp() {
        repository = mock(TrackingRuntimeRepositoryDispatcher.class);
        frameNumber = 0;
        config = shearConfig();
        when(repository.findConfigAs(UNIT, TrackingType.SHEAR, ShearTrackingConfig.class))
                .thenReturn(Optional.of(config));
        when(repository.findConfigAs(UNIT, TrackingType.STATUS, StatusTrackingConfig.class))
                .thenReturn(Optional.of(statusConfig()));
        when(repository.findRuntimeAs(eq(UNIT), eq(TrackingType.SHEAR), anyString(),
                eq(ShearTrackingRuntime.class))).thenAnswer(invocation ->
                Optional.ofNullable(runtimes.get(invocation.getArgument(2))));
        doAnswer(invocation -> {
            List<TrackingRuntime> values = invocation.getArgument(0);
            for (TrackingRuntime value : values) {
                ShearTrackingRuntime runtime = (ShearTrackingRuntime) value;
                runtimes.put(runtime.getDeviceCode(), runtime);
            }
            return null;
        }).when(repository).saveRuntimes(anyList());

        ShearDeviceResolver resolver = new ShearDeviceResolver();
        ShearRuntimeService runtimeService = new ShearRuntimeService();
        ReflectionTestUtils.setField(runtimeService, "runtimeRepositoryDispatcher", repository);
        algorithm = new ShearTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", repository);
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", mock(TrackingStepLogger.class));
        properties = new TrackingProperties();
        ReflectionTestUtils.setField(algorithm, "trackingProperties", properties);
        ReflectionTestUtils.setField(algorithm, "shearDeviceResolver", resolver);
        ReflectionTestUtils.setField(algorithm, "shearDecisionService", new ShearDecisionService(resolver));
        ReflectionTestUtils.setField(algorithm, "shearRuntimeService", runtimeService);
    }

    @Test
    void firstSliceAndFirstCutOfNextGroupHaveZeroLength() {
        ShearResult first = only(algorithm.calculate(input("500")));
        assertThat(first.getShearKind()).isEqualTo(ShearKind.SLICE);
        assertThat(first.getShearNo()).isEqualTo(1);
        assertThat(first.getCutNo()).isEqualTo(1);
        assertThat(first.getShearLength()).isEqualByComparingTo("0");
        algorithm.afterPersist(input("500"), Arrays.asList(first));

        ShearResult nextGroup = only(algorithm.calculate(input("300")));
        assertThat(nextGroup.getShearNo()).isEqualTo(2);
        assertThat(nextGroup.getCutNo()).isEqualTo(1);
        assertThat(nextGroup.getShearLength()).isEqualByComparingTo("0");
    }

    @Test
    void sameGroupUsesAbsoluteLengthDifference() {
        ShearResult first = only(algorithm.calculate(input("500")));
        algorithm.afterPersist(input("500"), Arrays.asList(first));

        ShearResult next = only(algorithm.calculate(input("550")));
        assertThat(next.getShearNo()).isEqualTo(1);
        assertThat(next.getCutNo()).isEqualTo(2);
        assertThat(next.getShearLength()).isEqualByComparingTo("50");
    }

    @Test
    void doesNotCommitCalculatedCounterUntilPersistenceCallback() {
        ShearResult firstAttempt = only(algorithm.calculate(input("500")));
        ShearResult retryBeforePersist = only(algorithm.calculate(input("500")));

        assertThat(firstAttempt.getCutNo()).isEqualTo(1);
        assertThat(retryBeforePersist.getCutNo()).isEqualTo(1);
        assertThat(runtimes).doesNotContainKey("por1");
    }

    @Test
    void duplicateTriggerFrameDoesNotIncrementOrPersistTwice() {
        TrackingInput input = input("500");
        List<ShearResult> first = algorithm.calculate(input);
        algorithm.afterPersist(input, first);

        assertThat(algorithm.calculate(input)).isEmpty();
        ShearTrackingRuntime runtime = runtimes.get("por1");
        assertThat(runtime.getSlice().getShearNo()).isEqualTo(1);
        assertThat(runtime.getSlice().getCutNo()).isEqualTo(1);
        assertThat(runtime.getLastPersistedTriggerTime()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void storageDisabledStillCommitsCountersWithoutIdempotencyTime() {
        properties.getStorage().getShear().setEnabled(false);
        ShearResult result = only(algorithm.calculate(input("500")));

        assertThat(result.getRuntimeCommit()).isNull();
        assertThat(runtimes.get("por1").getSlice().getCutNo()).isEqualTo(1);
        assertThat(runtimes.get("por1").getLastPersistedTriggerTime()).isNull();
    }

    @Test
    void updatesDeviceMaterialAndClearsCountersWhenMaterialChanges() {
        ShearResult first = only(algorithm.calculate(input("500")));
        algorithm.afterPersist(input("500"), Arrays.asList(first));

        TrackingInput changed = input("300");
        StatusCandidateRuntime candidate = changed.getStatusContext().getCandidates().get("por1");
        candidate.setCoilNo("NEW-COIL");
        candidate.setProductNo(2);
        candidate.setLengths(Arrays.asList(new BigDecimal("300")));
        ShearResult newMaterial = only(algorithm.calculate(changed));
        assertThat(newMaterial.getShearNo()).isEqualTo(1);
        assertThat(newMaterial.getCutNo()).isEqualTo(1);
        assertThat(newMaterial.getShearLength()).isEqualByComparingTo("0");
    }

    @Test
    void storesCounterOnMaterialDeviceAndIdempotencyTimeOnTriggerDevice() {
        ShearTrackingRuntime triggerRuntime = ShearTrackingRuntime.builder()
                .unitCode(UNIT).trackingType(TrackingType.SHEAR).deviceCode("tr1")
                .side(DeviceSide.COILER).coilNo("COIL-2").productNo(1)
                .tail(ShearCounterRuntime.builder().shearNo(1).cutNo(5)
                        .lastRemainingLength(new BigDecimal("200")).build()).build();
        runtimes.put("tr1", triggerRuntime);

        TrackingInput input = input("500");
        Map<String, Object> previous = new LinkedHashMap<>(input.getPreviousSnapshot().getValues());
        Map<String, Object> latest = new LinkedHashMap<>(input.getLatestSnapshot().getValues());
        previous.put("/line-x/shear/exit-cut", true);
        latest.put("/line-x/shear/exit-cut", false);
        latest.put("/line-x/shear/exit-color", "30");
        latest.put("/line-x/shear/welder-pieces", 0);
        latest.put("/line-x/shear/front-sample", 1);
        latest.put("/line-x/shear/front-scrap", 1);
        latest.put("/line-x/shear/front-length", new BigDecimal("1.2"));
        input.setPreviousSnapshot(PointSnapshot.builder().values(previous).build());
        input.setLatestSnapshot(PointSnapshot.builder().values(latest)
                .receivedAt(RECEIVED_AT.plusSeconds(frameNumber++)).build());

        ShearResult result = algorithm.calculate(input).stream()
                .filter(value -> "exit-cut".equals(value.getShearPointCode()))
                .findFirst().orElseThrow(AssertionError::new);
        assertThat(result.getDeviceCode()).isEqualTo("tr1");
        assertThat(result.getInMatDeviceCode()).isEqualTo("tr2");
        assertThat(result.getShearKind()).isEqualTo(ShearKind.HEAD);

        algorithm.afterPersist(input, Arrays.asList(result));
        assertThat(runtimes.get("tr2").getHead().getCutNo()).isEqualTo(1);
        assertThat(runtimes.get("tr1").getLastPersistedTriggerTime())
                .isEqualTo(input.getLatestSnapshot().getReceivedAt());
    }

    @Test
    void continuousCoilerSliceSelectsFirstOtherCandidateWithSameColor() {
        TrackingInput input = input("500");
        input.getStatusContext().getCandidates().get("tr2").setColorNo("20");
        triggerExit(input, "20");

        ShearResult result = exitResult(algorithm.calculate(input));
        assertThat(result.getShearKind()).isEqualTo(ShearKind.SLICE);
        assertThat(result.getDeviceCode()).isEqualTo("tr1");
        assertThat(result.getInMatDeviceCode()).isEqualTo("tr2");
        assertThat(result.getShearLength()).isEqualByComparingTo("0");
    }

    @Test
    void fixedContinuousCoilerSliceStillUsesColorToSelectMaterial() {
        config.getTracking().getCoilerShearPoint().get(0).getShearSettings()
                .setDefaultValue(ShearKind.SLICE);
        TrackingInput input = input("500");
        input.getStatusContext().getCandidates().get("tr2").setColorNo("20");
        triggerExit(input, "20");

        ShearResult result = exitResult(algorithm.calculate(input));

        assertThat(result.getShearKind()).isEqualTo(ShearKind.SLICE);
        assertThat(result.getInMatDeviceCode()).isEqualTo("tr2");
    }

    @Test
    void resolvesCandidatesInStatusOrderWithoutCurrentOrUnconfiguredFallback() {
        TrackingInput input = input("500");
        Map<String, StatusCandidateRuntime> candidates = input.getStatusContext().getCandidates();
        StatusCandidateRuntime por1 = candidates.get("por1");
        StatusCandidateRuntime tr1 = candidates.get("tr1");
        StatusCandidateRuntime tr2 = candidates.get("tr2");
        Map<String, StatusCandidateRuntime> reordered = new LinkedHashMap<>();
        reordered.put("tr2", tr2);
        reordered.put("extra", StatusCandidateRuntime.builder().deviceCode("extra")
                .deviceName("未配置设备").dataComplete(true).coilNo("COIL-X").productNo(1)
                .lengths(Arrays.asList(new BigDecimal("1"))).build());
        reordered.put("tr1", tr1);
        reordered.put("por1", por1);
        input.getStatusContext().setCandidates(reordered);
        Map<DeviceSide, StatusCurrentRuntime> current = new EnumMap<>(DeviceSide.class);
        current.put(DeviceSide.COILER, StatusCurrentRuntime.builder()
                .side(DeviceSide.COILER).deviceCode("por1").build());
        input.getStatusContext().setCurrent(current);

        List<ShearDeviceSnapshot> devices = new ShearDeviceResolver().resolveCandidates(
                input.getStatusContext(), statusConfig());

        assertThat(devices).extracting(ShearDeviceSnapshot::getDeviceCode)
                .containsExactly("por1", "tr1", "tr2");
        assertThat(devices.get(0).getSide()).isEqualTo(DeviceSide.UNCOILER);
    }

    @Test
    void countsUniqueOccupiedUncoilerDevicesBeforeUsingShortestLengthFallback() {
        ShearPointConfig firstEntryPoint = ShearPointConfig.builder().deviceCode("por1")
                .gratingPoints(Arrays.asList(GratingPointConfig.builder().name("grating-a")
                        .hasCoil(true).build())).build();
        ShearPointConfig secondEntryPoint = ShearPointConfig.builder().deviceCode("por1")
                .gratingPoints(Arrays.asList(GratingPointConfig.builder().name("grating-b")
                        .hasCoil(true).build())).build();
        ShearTrackingSection tracking = ShearTrackingSection.builder()
                .pointPrefix("/line-x/shear/")
                .uncoilerShearPoint(Arrays.asList(firstEntryPoint, secondEntryPoint)).build();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("/line-x/shear/grating-a", true);
        values.put("/line-x/shear/grating-b", true);
        PointSnapshot snapshot = PointSnapshot.builder().values(values).build();
        ShearDeviceSnapshot occupied = ShearDeviceSnapshot.builder().side(DeviceSide.UNCOILER)
                .deviceCode("por1").coilNo("COIL-1").productNo(1)
                .remainingLength(new BigDecimal("500")).dataComplete(true).build();
        ShearDeviceSnapshot shortest = ShearDeviceSnapshot.builder().side(DeviceSide.UNCOILER)
                .deviceCode("por2").coilNo("COIL-2").productNo(1)
                .remainingLength(new BigDecimal("100")).dataComplete(true).build();

        ShearDeviceSnapshot selected = new ShearDeviceResolver().selectHeadMaterial(
                snapshot, tracking, Arrays.asList(occupied, shortest));

        assertThat(selected.getDeviceCode()).isEqualTo("por1");
    }

    @Test
    void discontinuousCoilerDistinguishesEmptySameAndDifferentCoil() {
        config.getTracking().setMode(ShearMode.DISCONTINUOUS);

        TrackingInput emptyCoil = input("500");
        StatusCandidateRuntime emptyTrigger = emptyCoil.getStatusContext().getCandidates().get("tr1");
        emptyTrigger.setCoilNo(null);
        emptyTrigger.setProductNo(null);
        emptyTrigger.setDataComplete(false);
        emptyTrigger.setLengths(java.util.Collections.emptyList());
        triggerExit(emptyCoil, null);
        ShearResult head = exitResult(algorithm.calculate(emptyCoil));
        assertThat(head.getShearKind()).isEqualTo(ShearKind.HEAD);
        assertThat(head.getInMatDeviceCode()).isEqualTo("por1");

        TrackingInput sameCoil = input("500");
        sameCoil.getStatusContext().getCandidates().get("tr2").setCoilNo("COIL-2");
        triggerExit(sameCoil, null);
        ShearResult slice = exitResult(algorithm.calculate(sameCoil));
        assertThat(slice.getShearKind()).isEqualTo(ShearKind.SLICE);
        assertThat(slice.getInMatDeviceCode()).isEqualTo("tr2");

        TrackingInput differentCoil = input("500");
        triggerExit(differentCoil, null);
        ShearResult tail = exitResult(algorithm.calculate(differentCoil));
        assertThat(tail.getShearKind()).isEqualTo(ShearKind.TAIL);
        assertThat(tail.getInMatDeviceCode()).isEqualTo("tr1");
    }

    private TrackingInput input(String remainingLength) {
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("/line-x/shear/entry-cut", true);
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("/line-x/shear/entry-cut", false);
        previous.put("/line-x/shear/exit-cut", true);
        latest.put("/line-x/shear/exit-cut", true);
        latest.put("/line-x/shear/entry-grating", true);
        latest.put("/line-x/shear/head-length", new BigDecimal("2.5"));
        latest.put("/line-x/shear/head-number", 2);
        latest.put("/line-x/shear/tail-length", new BigDecimal("3.5"));
        latest.put("/line-x/shear/tail-number", 3);
        latest.put("/line-x/shear/exit-head-length", new BigDecimal("4.5"));
        latest.put("/line-x/shear/exit-head-number", 4);
        latest.put("/line-x/shear/exit-tail-length", new BigDecimal("6.5"));
        latest.put("/line-x/shear/exit-tail-number", 6);
        StatusCandidateRuntime feed = StatusCandidateRuntime.builder().deviceCode("por1")
                .deviceName("1#开卷机").dataComplete(true).coilNo("COIL-1").productNo(1)
                .colorNo("10").lengths(Arrays.asList(new BigDecimal(remainingLength)))
                .maxLength(new BigDecimal("1000")).build();
        StatusCandidateRuntime take = StatusCandidateRuntime.builder().deviceCode("tr1")
                .deviceName("1#卷取机").dataComplete(true).coilNo("COIL-2").productNo(1)
                .colorNo("20").lengths(Arrays.asList(new BigDecimal("200")))
                .maxLength(new BigDecimal("800")).build();
        Map<String, StatusCandidateRuntime> candidates = new LinkedHashMap<>();
        candidates.put("por1", feed);
        candidates.put("tr1", take);
        candidates.put("tr2", StatusCandidateRuntime.builder().deviceCode("tr2")
                .deviceName("2#卷取机").dataComplete(true).coilNo("COIL-3").productNo(1)
                .colorNo("30").lengths(Arrays.asList(new BigDecimal("100")))
                .maxLength(new BigDecimal("800")).build());
        return TrackingInput.builder().unitCode(UNIT).trackingType(TrackingType.SHEAR)
                .previousSnapshot(PointSnapshot.builder().values(previous).build())
                .latestSnapshot(PointSnapshot.builder().values(latest)
                        .receivedAt(RECEIVED_AT.plusSeconds(frameNumber++)).build())
                .statusContext(StatusTrackingContext.builder().candidates(candidates).build()).build();
    }

    private ShearTrackingConfig shearConfig() {
        ShearPointConfig point = ShearPointConfig.builder().name("entry-cut")
                .type(PointDataType.BOOLEAN).normalPos(true).deviceCode("por1")
                .gratingPoints(Arrays.asList(GratingPointConfig.builder().name("entry-grating")
                        .type(PointDataType.BOOLEAN).hasCoil(true).build()))
                .typeCodes(ShearTypeCodes.builder().head("111").slice("115").tail("119").build())
                .shearSettings(ShearSettings.builder()
                        .head(CutSetting.builder().number(point("head-number"))
                                .length(point("head-length")).build())
                        .tail(CutSetting.builder().number(point("tail-number"))
                                .length(point("tail-length")).build()).build())
                .build();
        return ShearTrackingConfig.builder().unitCode(UNIT).trackingType(TrackingType.SHEAR)
                .enable(true).tracking(ShearTrackingSection.builder().pointPrefix("/line-x/shear/")
                        .mode(ShearMode.CONTINUOUS).tailExperience(new BigDecimal("50"))
                        .shearExperience(new BigDecimal("100"))
                        .uncoilerShearPoint(Arrays.asList(point))
                        .coilerShearPoint(Arrays.asList(exitPoint())).build()).build();
    }

    private StatusTrackingConfig statusConfig() {
        return StatusTrackingConfig.builder().unitCode(UNIT)
                .tracking(StatusTrackingSection.builder().points(Arrays.asList(
                        StatusPointGroup.builder().code("por1").name("1#开卷机")
                                .side(DeviceSide.UNCOILER).build(),
                        StatusPointGroup.builder().code("tr1").name("1#卷取机")
                                .side(DeviceSide.COILER).build(),
                        StatusPointGroup.builder().code("tr2").name("2#卷取机")
                                .side(DeviceSide.COILER).build())).build()).build();
    }

    private ShearPointConfig exitPoint() {
        return ShearPointConfig.builder().name("exit-cut").type(PointDataType.BOOLEAN)
                .normalPos(true).deviceCodes(Arrays.asList("tr1", "tr2"))
                .typeCodes(ShearTypeCodes.builder().head("901").slice("905").tail("909").build())
                .colorPoint(point("exit-color"))
                .shearSettings(ShearSettings.builder().welderPieces(point("welder-pieces"))
                .frontWelder(com.wisdri.tracking.domain.model.config.shear.WelderShearSettings.builder()
                                .samplePieces(point("front-sample")).scrapPieces(point("front-scrap"))
                                .length(point("front-length")).build())
                        .behindWelder(com.wisdri.tracking.domain.model.config.shear.WelderShearSettings.builder()
                                .samplePieces(point("front-sample")).scrapPieces(point("front-scrap"))
                                .length(point("front-length")).build())
                        .head(CutSetting.builder().number(point("exit-head-number"))
                                .length(point("exit-head-length")).build())
                        .tail(CutSetting.builder().number(point("exit-tail-number"))
                                .length(point("exit-tail-length")).build()).build())
                .build();
    }

    private void triggerExit(TrackingInput input, String shearColor) {
        Map<String, Object> previous = new LinkedHashMap<>(input.getPreviousSnapshot().getValues());
        Map<String, Object> latest = new LinkedHashMap<>(input.getLatestSnapshot().getValues());
        previous.put("/line-x/shear/exit-cut", true);
        latest.put("/line-x/shear/exit-cut", false);
        if (shearColor != null) {
            latest.put("/line-x/shear/exit-color", shearColor);
        }
        latest.put("/line-x/shear/welder-pieces", 0);
        latest.put("/line-x/shear/front-sample", 1);
        latest.put("/line-x/shear/front-scrap", 1);
        latest.put("/line-x/shear/front-length", new BigDecimal("1.2"));
        input.setPreviousSnapshot(PointSnapshot.builder().values(previous).build());
        input.setLatestSnapshot(PointSnapshot.builder().values(latest)
                .receivedAt(RECEIVED_AT.plusSeconds(frameNumber++)).build());
    }

    private ShearResult exitResult(List<ShearResult> results) {
        return results.stream().filter(result -> "exit-cut".equals(result.getShearPointCode()))
                .findFirst().orElseThrow(AssertionError::new);
    }

    private PointConfig point(String name) {
        return PointConfig.builder().name(name).type(PointDataType.FLOAT).build();
    }

    private ShearResult only(List<ShearResult> results) {
        assertThat(results).hasSize(1);
        return results.get(0);
    }
}
