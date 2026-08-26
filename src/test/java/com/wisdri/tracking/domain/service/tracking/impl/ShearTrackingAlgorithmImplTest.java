package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.shear.CutSetting;
import com.wisdri.tracking.domain.model.config.shear.ShearMode;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearSettings;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.config.shear.ShearTypeCodes;
import com.wisdri.tracking.domain.model.config.shear.WelderShearSettings;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import com.wisdri.tracking.domain.model.tracking.shear.ShearResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.tracking.trace.TrackingStepLogger;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShearTrackingAlgorithmImplTest {
    private static final String UNIT = "LINE-X";
    private final AtomicReference<ShearTrackingRuntime> runtime = new AtomicReference<>();
    private ShearTrackingAlgorithmImpl algorithm;
    private ShearTrackingConfig config;
    private TrackingProperties properties;

    @BeforeEach
    void setUp() {
        TrackingRuntimeRepositoryDispatcher repository = mock(TrackingRuntimeRepositoryDispatcher.class);
        config = config();
        when(repository.findConfigAs(UNIT, TrackingType.SHEAR, ShearTrackingConfig.class))
                .thenReturn(Optional.of(config));
        when(repository.findRuntimeAs(UNIT, TrackingType.SHEAR, ShearTrackingRuntime.class))
                .thenAnswer(invocation -> Optional.ofNullable(runtime.get()));
        doAnswer(invocation -> {
            runtime.set((ShearTrackingRuntime) invocation.getArgument(0));
            return null;
        }).when(repository).saveRuntime(any(TrackingRuntime.class));

        properties = new TrackingProperties();
        algorithm = new ShearTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", repository);
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", mock(TrackingStepLogger.class));
        ReflectionTestUtils.setField(algorithm, "trackingProperties", properties);
    }

    @Test
    void detectsConfiguredEdgeAndUsesConfiguredTypeCode() {
        ShearResult first = only(calculate(entryValues(true, "10", "2.5"),
                entryValues(false, "10.0", "2.5"), context("10", "20", "500")));

        assertThat(first.getShearPointCode()).isEqualTo("entry-cut-x");
        assertThat(first.getShearKind()).isEqualTo(ShearKind.HEAD);
        assertThat(first.getShearType()).isEqualTo(711);
        assertThat(first.getInMatNo()).isEqualTo("FEED-COIL");
        assertThat(first.getCutNo()).isEqualTo(1);
        assertThat(first.getShearLength()).isEqualByComparingTo("0");
        assertThat(first.getSetNumber()).isNull();

        algorithm.afterPersist(input(entryValues(true, "10", "2.5"),
                entryValues(false, "10", "2.5"), context("10", "20", "500")), Arrays.asList(first));
        ShearResult second = only(calculate(entryValues(true, "10", "2.5"),
                entryValues(false, "10", "2.5"), context("10", "20", "450")));

        assertThat(second.getShearNo()).isEqualTo(1);
        assertThat(second.getCutNo()).isEqualTo(2);
        assertThat(second.getShearLength()).isEqualByComparingTo("2.5");
        assertThat(second.getSetNumber()).isEqualTo(2);
    }

    @Test
    void classifiesUncoilerTailAndSliceAtConfiguredBoundary() {
        ShearResult tail = only(calculate(entryValues(true, "10", "2.5"),
                entryValues(false, "10", "2.5"), context("10", "10", "50")));
        assertThat(tail.getShearKind()).isEqualTo(ShearKind.TAIL);
        assertThat(tail.getShearType()).isEqualTo(719);
        assertThat(tail.getInMatNo()).isEqualTo("TAKE-COIL");
        algorithm.afterPersist(input(entryValues(true, "10", "2.5"),
                entryValues(false, "10", "2.5"), context("10", "10", "50")), Arrays.asList(tail));
        ShearResult secondTail = only(calculate(entryValues(true, "10", "2.5"),
                entryValues(false, "10", "2.5"), context("10", "10", "40")));
        assertThat(secondTail.getShearLength()).isEqualByComparingTo("3.5");
        assertThat(secondTail.getSetNumber()).isEqualTo(3);

        ShearResult slice = only(calculate(entryValues(true, "10", "2.5"),
                entryValues(false, "10", "2.5"), context("10", "10", "51")));
        assertThat(slice.getShearKind()).isEqualTo(ShearKind.SLICE);
        assertThat(slice.getShearType()).isEqualTo(715);
        assertThat(slice.getInMatNo()).isEqualTo("FEED-COIL");
    }

    @Test
    void switchesCoilerFromTailToHeadUsingConfiguredPieceLimit() {
        Map<String, Object> previous = exitValues(true, "30", "0", "0", "1.2", "1.8");
        Map<String, Object> latest = exitValues(false, "30", "0", "0", "1.2", "1.8");
        ShearResult tail = only(calculate(previous, latest, context("10", "20", "500")));
        assertThat(tail.getShearKind()).isEqualTo(ShearKind.TAIL);
        assertThat(tail.getShearType()).isEqualTo(939);
        assertThat(tail.getSetNumber()).isNull();
        algorithm.afterPersist(input(previous, latest, context("10", "20", "500")), Arrays.asList(tail));

        ShearResult head = only(calculate(previous, latest, context("10", "20", "450")));
        assertThat(head.getShearKind()).isEqualTo(ShearKind.HEAD);
        assertThat(head.getShearType()).isEqualTo(931);
        assertThat(head.getInMatNo()).isEqualTo("FEED-COIL");
        assertThat(head.getShearLength()).isEqualByComparingTo("1.8");
        assertThat(head.getSetNumber()).isEqualTo(5);
    }

    @Test
    void addsOddWelderPiecesToFrontSetNumberUsingIntegerSplit() {
        Map<String, Object> previous = exitValues(true, "30", "1", "1", "1.2", "1.8");
        Map<String, Object> latest = exitValues(false, "30", "1", "1", "1.2", "1.8");
        previous.put("/line-x/shear/welder-pieces", 5);
        latest.put("/line-x/shear/welder-pieces", 5);
        ShearResult first = only(calculate(previous, latest, context("10", "20", "500")));
        algorithm.afterPersist(input(previous, latest, context("10", "20", "500")), Arrays.asList(first));

        ShearResult second = only(calculate(previous, latest, context("10", "20", "450")));

        assertThat(second.getShearKind()).isEqualTo(ShearKind.TAIL);
        assertThat(second.getShearLength()).isEqualByComparingTo("1.2");
        assertThat(second.getSetNumber()).isEqualTo(4);
    }

    @Test
    void assignsOddWelderPieceRemainderToBehindSetNumber() {
        config.getTracking().getCoilerShearPoint().get(0)
                .getShearSettings().setDefaultValue(ShearKind.HEAD);
        Map<String, Object> previous = exitValues(true, "30", "1", "1", "1.2", "1.8");
        Map<String, Object> latest = exitValues(false, "30", "1", "1", "1.2", "1.8");
        previous.put("/line-x/shear/welder-pieces", 5);
        latest.put("/line-x/shear/welder-pieces", 5);

        ShearResult result = only(calculate(previous, latest, context("10", "20", "500")));

        assertThat(result.getShearKind()).isEqualTo(ShearKind.HEAD);
        assertThat(result.getSetNumber()).isEqualTo(8);
    }

    @Test
    void selectsSampleAndScrapLengthByWelderPieceSequence() {
        WelderShearSettings front = config.getTracking().getCoilerShearPoint().get(0)
                .getShearSettings().getFrontWelder();
        front.setLength(null);
        front.setSampleLength(point("sample-piece-length"));
        front.setScrapLength(point("scrap-piece-length"));
        Map<String, Object> previous = exitValues(true, "30", "1", "1", "1.2", "1.8");
        previous.put("/line-x/shear/sample-piece-length", new BigDecimal("4.2"));
        previous.put("/line-x/shear/scrap-piece-length", new BigDecimal("0.8"));
        Map<String, Object> latest = exitValues(false, "30", "1", "1", "1.2", "1.8");
        latest.put("/line-x/shear/sample-piece-length", new BigDecimal("4.2"));
        latest.put("/line-x/shear/scrap-piece-length", new BigDecimal("0.8"));

        ShearResult first = only(calculate(previous, latest, context("10", "20", "500")));
        algorithm.afterPersist(input(previous, latest, context("10", "20", "500")), Arrays.asList(first));
        ShearResult sample = only(calculate(previous, latest, context("10", "20", "450")));
        assertThat(sample.getShearLength()).isEqualByComparingTo("4.2");
        algorithm.afterPersist(input(previous, latest, context("10", "20", "450")), Arrays.asList(sample));

        ShearResult scrap = only(calculate(previous, latest, context("10", "20", "400")));
        assertThat(scrap.getShearLength()).isEqualByComparingTo("0.8");
        assertThat(scrap.getSetNumber()).isEqualTo(2);
    }

    @Test
    void ignoresFirstFrameReturnEdgeAndMissingStatusContext() {
        assertThat(algorithm.calculate(TrackingInput.builder()
                .unitCode(UNIT).trackingType(TrackingType.SHEAR)
                .latestSnapshot(snapshot(entryValues(false, "10", "2.5")))
                .statusContext(context("10", "20", "500")).build())).isEmpty();

        assertThat(calculate(entryValues(false, "10", "2.5"),
                entryValues(true, "10", "2.5"), context("10", "20", "500"))).isEmpty();
        assertThat(calculate(entryValues(true, "10", "2.5"),
                entryValues(false, "10", "2.5"), null)).isEmpty();

        Map<String, Object> invalid = entryValues(false, "10", "2.5");
        invalid.put("/line-x/shear/entry-cut-x", "invalid");
        assertThat(calculate(entryValues(true, "10", "2.5"), invalid,
                context("10", "20", "500"))).isEmpty();
    }

    @Test
    void skipsTriggeredShearWhenConfiguredCandidateIsIncompleteInCurrentFrame() {
        StatusTrackingContext context = context("10", "20", "500");
        context.getCurrent().get(DeviceSide.UNCOILER).setDeviceCode(null);
        context.getCandidates().get("feed-device-x").setDataComplete(false);

        List<ShearResult> results = calculate(entryValues(true, "10", "2.5"),
                entryValues(false, "10", "2.5"), context);

        assertThat(results).isEmpty();
    }

    @Test
    void matchesConfiguredCodesAgainstCurrentAndFallsBackToLastCode() {
        ShearPointConfig exit = config.getTracking().getCoilerShearPoint().get(0);
        exit.setPorTrCodes(Arrays.asList("take-device-current", "take-device-x"));

        StatusTrackingContext currentMatched = context("10", "20", "500");
        currentMatched.getCurrent().get(DeviceSide.COILER).setDeviceCode("take-device-current");
        assertThat(calculate(exitValues(true, "30", "0", "0", "1.2", "1.8"),
                exitValues(false, "30", "0", "0", "1.2", "1.8"), currentMatched)).hasSize(1);

        StatusTrackingContext fallback = context("10", "20", "500");
        fallback.getCurrent().get(DeviceSide.COILER).setDeviceCode(null);
        ShearResult result = only(calculate(exitValues(true, "30", "0", "0", "1.2", "1.8"),
                exitValues(false, "30", "0", "0", "1.2", "1.8"), fallback));
        assertThat(result.getTrCoilNo()).isEqualTo("TAKE-COIL");
    }

    @Test
    void equalExperienceStartsNewCompleteShear() {
        Map<String, Object> previous = entryValues(true, "10", "2.5");
        Map<String, Object> latest = entryValues(false, "10", "2.5");
        ShearResult first = only(calculate(previous, latest, context("10", "20", "500")));
        algorithm.afterPersist(input(previous, latest, context("10", "20", "500")), Arrays.asList(first));

        ShearResult next = only(calculate(previous, latest, context("10", "20", "400")));
        assertThat(next.getShearNo()).isEqualTo(2);
        assertThat(next.getCutNo()).isEqualTo(1);
    }

    @Test
    void supportsReverseNormalPositionAndMultipleShearsInOneFrame() {
        config.getTracking().getUncoilerShearPoint().get(0).setNormalPos(false);
        Map<String, Object> previous = entryValues(false, "10", "2.5");
        previous.putAll(exitValues(true, "20", "0", "0", "1.2", "1.8"));
        Map<String, Object> latest = entryValues(true, "10", "2.5");
        latest.putAll(exitValues(false, "20", "0", "0", "1.2", "1.8"));

        List<ShearResult> results = calculate(previous, latest, context("10", "20", "500"));

        assertThat(results).extracting(ShearResult::getShearPointCode)
                .containsExactly("entry-cut-x", "exit-cut-x");
        assertThat(results).extracting(ShearResult::getShearType)
                .containsExactly(711, 935);
    }

    @Test
    void disabledStorageDoesNotCommitRuntime() {
        properties.getStorage().getShear().setEnabled(false);
        Map<String, Object> previous = entryValues(true, "10", "2.5");
        Map<String, Object> latest = entryValues(false, "10", "2.5");
        ShearResult result = only(calculate(previous, latest, context("10", "20", "500")));

        algorithm.afterPersist(input(previous, latest, context("10", "20", "500")),
                Arrays.asList(result));

        assertThat(runtime.get()).isNull();
    }

    private List<ShearResult> calculate(Map<String, Object> previous,
                                        Map<String, Object> latest,
                                        StatusTrackingContext context) {
        return algorithm.calculate(input(previous, latest, context));
    }

    private TrackingInput input(Map<String, Object> previous,
                                Map<String, Object> latest,
                                StatusTrackingContext context) {
        return TrackingInput.builder()
                .unitCode(UNIT)
                .trackingType(TrackingType.SHEAR)
                .previousSnapshot(snapshot(previous))
                .latestSnapshot(snapshot(latest))
                .statusContext(context)
                .build();
    }

    private PointSnapshot snapshot(Map<String, Object> values) {
        return PointSnapshot.builder().values(values).receivedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    private StatusTrackingContext context(String feedColor, String takeColor, String feedLength) {
        Map<String, StatusCandidateRuntime> candidates = new LinkedHashMap<>();
        candidates.put("feed-device-x", StatusCandidateRuntime.builder()
                .dataComplete(true)
                .coilNo("FEED-COIL").productNo(3).colorNo(feedColor)
                .lengths(Arrays.asList(new BigDecimal(feedLength))).maxLength(new BigDecimal("1000")).build());
        candidates.put("take-device-x", StatusCandidateRuntime.builder()
                .dataComplete(true)
                .coilNo("TAKE-COIL").productNo(4).colorNo(takeColor)
                .lengths(Arrays.asList(new BigDecimal("200"))).maxLength(new BigDecimal("800")).build());
        Map<DeviceSide, StatusCurrentRuntime> current = new LinkedHashMap<>();
        current.put(DeviceSide.UNCOILER, StatusCurrentRuntime.builder()
                .side(DeviceSide.UNCOILER).deviceCode("feed-device-x")
                .coilNo("FEED-COIL").productNo(3).colorNo(feedColor)
                .remainingLength(new BigDecimal(feedLength)).maxLength(new BigDecimal("1000")).build());
        current.put(DeviceSide.COILER, StatusCurrentRuntime.builder()
                .side(DeviceSide.COILER).deviceCode("take-device-x")
                .coilNo("TAKE-COIL").productNo(4).colorNo(takeColor)
                .remainingLength(new BigDecimal("200")).maxLength(new BigDecimal("800")).build());
        return StatusTrackingContext.builder().candidates(candidates).current(current).build();
    }

    private Map<String, Object> entryValues(boolean signal, String color, String headLength) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("/line-x/shear/entry-cut-x", signal);
        values.put("/line-x/shear/entry-color", color);
        values.put("/line-x/shear/head-length", new BigDecimal(headLength));
        values.put("/line-x/shear/head-number", 2);
        values.put("/line-x/shear/tail-length", new BigDecimal("3.5"));
        values.put("/line-x/shear/tail-number", 3);
        return values;
    }

    private Map<String, Object> exitValues(boolean signal,
                                           String color,
                                           String sample,
                                           String scrap,
                                           String frontLength,
                                           String rearLength) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("/line-x/shear/exit-cut-x", signal);
        values.put("/line-x/shear/exit-color", color);
        values.put("/line-x/shear/front-sample", new BigDecimal(sample));
        values.put("/line-x/shear/front-scrap", new BigDecimal(scrap));
        values.put("/line-x/shear/front-length", new BigDecimal(frontLength));
        values.put("/line-x/shear/rear-sample", 2);
        values.put("/line-x/shear/rear-scrap", 3);
        values.put("/line-x/shear/rear-length", new BigDecimal(rearLength));
        values.put("/line-x/shear/welder-pieces", 0);
        return values;
    }

    private ShearResult only(List<ShearResult> results) {
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    private ShearTrackingConfig config() {
        ShearPointConfig entry = ShearPointConfig.builder()
                .name("entry-cut-x").type(PointDataType.BOOLEAN).normalPos(true)
                .porTrCode("feed-device-x")
                .typeCodes(ShearTypeCodes.builder().head(711).slice(715).tail(719).build())
                .colorPoint(point("entry-color"))
                .shearSettings(ShearSettings.builder()
                        .head(CutSetting.builder().number(point("head-number"))
                                .length(point("head-length")).build())
                        .tail(CutSetting.builder().number(point("tail-number"))
                                .length(point("tail-length")).build())
                        .build())
                .build();
        ShearPointConfig exit = ShearPointConfig.builder()
                .name("exit-cut-x").type(PointDataType.BOOLEAN).normalPos(true)
                .porTrCodes(Arrays.asList("take-device-y", "take-device-x"))
                .typeCodes(ShearTypeCodes.builder().head(931).slice(935).tail(939).build())
                .colorPoint(point("exit-color"))
                .shearSettings(ShearSettings.builder()
                        .welderPieces(point("welder-pieces"))
                        .frontWelder(WelderShearSettings.builder()
                                .samplePieces(point("front-sample"))
                                .scrapPieces(point("front-scrap"))
                                .length(point("front-length")).build())
                        .behindWelder(WelderShearSettings.builder()
                                .samplePieces(point("rear-sample"))
                                .scrapPieces(point("rear-scrap"))
                                .length(point("rear-length")).build())
                        .build())
                .build();
        return ShearTrackingConfig.builder()
                .unitCode(UNIT).trackingType(TrackingType.SHEAR).enable(true).mqttTopic("line-x-shear")
                .tracking(ShearTrackingSection.builder()
                        .pointPrefix("/line-x/shear/").mode(ShearMode.CONTINUOUS)
                        .tailExperience(new BigDecimal("50"))
                        .shearExperience(new BigDecimal("100"))
                        .uncoilerShearPoint(Arrays.asList(entry))
                        .coilerShearPoint(Arrays.asList(exit))
                        .build())
                .build();
    }

    private PointConfig point(String name) {
        return PointConfig.builder().name(name).type(PointDataType.FLOAT).build();
    }
}
