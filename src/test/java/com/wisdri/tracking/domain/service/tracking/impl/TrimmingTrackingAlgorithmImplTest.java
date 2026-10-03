package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingLengthMode;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingConfig;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.runtime.trimming.TrimmingSegmentRuntime;
import com.wisdri.tracking.domain.model.runtime.trimming.TrimmingTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.model.tracking.trimming.TrimmingResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrimmingTrackingAlgorithmImplTest {
    private TrackingRuntimeRepositoryDispatcher runtimeRepository;
    private TrimmingTrackingAlgorithmImpl algorithm;

    @BeforeEach
    void setUp() {
        runtimeRepository = mock(TrackingRuntimeRepositoryDispatcher.class);
        algorithm = new TrimmingTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", runtimeRepository);
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", mock(TrackingStepLogger.class));
        ReflectionTestUtils.setField(algorithm, "trackingProperties", new TrackingProperties());
        doNothing().when(runtimeRepository).saveRuntime(any(TrackingRuntime.class));
    }

    @Test
    void statusModeCreatesZeroRecordAndCommitsRuntimeAfterPersist() {
        TrimmingTrackingConfig config = config(TrimmingLengthMode.STATUS);
        stub(config, Optional.empty());
        TrackingInput input = input(values("1000.5", "1000"), status("C001", 2, true));

        TrimmingResult result = algorithm.calculate(input).get(0);

        assertThat(result.getInMatNo()).isEqualTo("C001");
        assertThat(result.getRepeatProdNo()).isEqualTo(2);
        assertThat(result.getTrimmingLength()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getUpdateExisting()).isFalse();
        algorithm.afterPersist(input, Collections.singletonList(result));

        ArgumentCaptor<TrackingRuntime> runtime = ArgumentCaptor.forClass(TrackingRuntime.class);
        verify(runtimeRepository).saveRuntime(runtime.capture());
        TrimmingSegmentRuntime disc = ((TrimmingTrackingRuntime) runtime.getValue())
                .getSegments().get("disc");
        assertThat(disc.getCoilNo()).isEqualTo("C001");
        assertThat(disc.getRepeatProdNo()).isEqualTo(2);
        assertThat(disc.getCoilWidthPv()).isEqualByComparingTo("1000.5");
        assertThat(disc.getCoilWidthSv()).isEqualByComparingTo("1000");
        assertThat(disc.getTrimmingLength()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void updatesExistingZeroRecordWhenCalculatedTrimmingBecomesPositive() {
        TrimmingTrackingConfig config = config(TrimmingLengthMode.STATUS);
        stub(config, Optional.of(runtime("C001", 2, BigDecimal.ZERO)));

        TrimmingResult result = algorithm.calculate(
                input(values("1005", "1000"), status("C001", 2, true))).get(0);

        assertThat(result.getTrimmingLength()).isEqualByComparingTo("2.5");
        assertThat(result.getUpdateExisting()).isTrue();
    }

    @Test
    void skipsExistingNonZeroRecord() {
        TrimmingTrackingConfig config = config(TrimmingLengthMode.STATUS);
        stub(config, Optional.of(runtime("C001", 2, new BigDecimal("2.5"))));

        assertThat(algorithm.calculate(
                input(values("1010", "1000"), status("C001", 2, true)))).isEmpty();
    }

    @Test
    void welderModeUsesSmallestNonNegativeCorrectedLengthAndMatchesProductNumber() {
        TrimmingTrackingConfig config = config(TrimmingLengthMode.WELDER);
        config.getSegments().get(0).setLengthCorrect(new BigDecimal("-2"));
        config.getTracking().setPoints(Arrays.asList(
                group("coil1", "length1"), group("coil2", "length2")));
        stub(config, Optional.empty());
        Map<String, Object> values = values("1004", "1000");
        values.put("coil1", "C001");
        values.put("length1", new BigDecimal("12"));
        values.put("coil2", "C002");
        values.put("length2", new BigDecimal("5"));
        StatusTrackingContext context = status("OTHER", 1, true);
        context.setCandidates(Collections.singletonMap("por2", StatusCandidateRuntime.builder()
                .coilNo("C002").repeatProdNo(7).build()));

        TrimmingResult result = algorithm.calculate(input(values, context)).get(0);

        assertThat(result.getInMatNo()).isEqualTo("C002");
        assertThat(result.getRepeatProdNo()).isEqualTo(7);
        assertThat(result.getHeadLength()).isEqualByComparingTo("3");
        assertThat(result.getTrimmingLength()).isEqualByComparingTo("2");
    }

    private void stub(TrimmingTrackingConfig config, Optional<TrimmingTrackingRuntime> runtime) {
        when(runtimeRepository.findConfigAs(
                "CP1", TrackingType.TRIMMING, TrimmingTrackingConfig.class))
                .thenReturn(Optional.of(config));
        when(runtimeRepository.findRuntimeAs(
                "CP1", TrackingType.TRIMMING, TrimmingTrackingRuntime.class))
                .thenReturn(runtime);
    }

    private TrimmingTrackingConfig config(TrimmingLengthMode mode) {
        TrimmingTrackingSection tracking = TrimmingTrackingSection.builder()
                .speedPoint(PointConfig.builder().name("speed").build())
                .startCondition(StartCondition.builder()
                        .point(PointConfig.builder().name("speed").build())
                        .threshold(BigDecimal.ZERO).build())
                .lengthMode(mode).build();
        SegmentConfig disc = SegmentConfig.builder()
                .code("disc").lengthCorrect(BigDecimal.ZERO).lengthArrayIndex(0)
                .points(Arrays.asList(
                        PointConfig.builder().name("widthPv").build(),
                        PointConfig.builder().name("widthSv").build()))
                .build();
        return TrimmingTrackingConfig.builder()
                .unitCode("CP1").trackingType(TrackingType.TRIMMING)
                .tracking(tracking).segments(Collections.singletonList(disc)).build();
    }

    private TrackingPointGroup group(String coil, String length) {
        return TrackingPointGroup.builder()
                .coilNo(PointConfig.builder().name(coil).build())
                .length(Collections.singletonList(PointConfig.builder().name(length).build()))
                .build();
    }

    private TrimmingTrackingRuntime runtime(String coilNo, int repeatProdNo, BigDecimal trimmingLength) {
        return TrimmingTrackingRuntime.builder()
                .unitCode("CP1").trackingType(TrackingType.TRIMMING)
                .segments(Collections.singletonMap("disc", TrimmingSegmentRuntime.builder()
                        .segmentCode("disc").coilNo(coilNo).repeatProdNo(repeatProdNo)
                        .trimmingLength(trimmingLength).build()))
                .build();
    }

    private StatusTrackingContext status(String coilNo, int repeatProdNo, boolean running) {
        Map<DeviceSide, StatusCurrentRuntime> current = new EnumMap<>(DeviceSide.class);
        current.put(DeviceSide.UNCOILER, StatusCurrentRuntime.builder()
                .side(DeviceSide.UNCOILER).running(running)
                .coilNo(coilNo).repeatProdNo(repeatProdNo).build());
        return StatusTrackingContext.builder().current(current).build();
    }

    private Map<String, Object> values(String widthPv, String widthSv) {
        Map<String, Object> values = new HashMap<>();
        values.put("speed", BigDecimal.ONE);
        values.put("widthPv", new BigDecimal(widthPv));
        values.put("widthSv", new BigDecimal(widthSv));
        return values;
    }

    private TrackingInput input(Map<String, Object> values, StatusTrackingContext context) {
        return TrackingInput.builder()
                .unitCode("CP1").trackingType(TrackingType.TRIMMING)
                .latestSnapshot(PointSnapshot.builder().values(values)
                        .receivedAt(Instant.parse("2026-08-29T01:00:00Z")).build())
                .statusContext(context).build();
    }
}
