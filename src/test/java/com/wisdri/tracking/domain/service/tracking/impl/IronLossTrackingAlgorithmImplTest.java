package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossSegmentConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingSection;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.ironloss.IronLossResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IronLossTrackingAlgorithmImplTest {
    private static final String TRACKING_PREFIX = "/aygg_tracking/fcl1/ironloss/tracking/";
    private static final String TECH_PREFIX = "/aygg_tracking/fcl1/ironloss/tech/iron_loss/";
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-03T00:00:00Z");

    private IronLossTrackingAlgorithmImpl algorithm;
    private IronLossTrackingConfig config;

    @BeforeEach
    void setUp() {
        IronLossTrackingSection tracking = new IronLossTrackingSection();
        tracking.setPointPrefix(TRACKING_PREFIX);
        tracking.setCoilNo(point("coil_no"));
        tracking.setLength(point("length"));
        tracking.setStartCondition(StartCondition.builder()
                .point(point("length")).threshold(new BigDecimal("0.1")).build());
        IronLossSegmentConfig segment = new IronLossSegmentConfig();
        segment.setCode("iron_loss");
        segment.setPointPrefix(TECH_PREFIX);
        segment.setCellCodeValue(1);
        segment.setPoints(Arrays.asList(point("thickness"), point("iron_loss")));
        config = IronLossTrackingConfig.builder().unitCode("FCL1")
                .trackingType(TrackingType.IRONLOSS).tracking(tracking)
                .segments(Collections.singletonList(segment)).build();
        TrackingRuntimeRepositoryDispatcher dispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        when(dispatcher.findConfigAs("FCL1", TrackingType.IRONLOSS, IronLossTrackingConfig.class))
                .thenReturn(Optional.of(config));
        algorithm = new IronLossTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", dispatcher);
    }

    @Test
    void thresholdIsInclusiveAndOriginalParametersArePreserved() {
        Map<String, Object> values = values(new BigDecimal("0.1"));
        values.put(TECH_PREFIX + "iron_loss", new BigDecimal("1.234567890123456789"));
        StatusTrackingContext status = StatusTrackingContext.builder()
                .current(Collections.singletonMap(DeviceSide.UNCOILER,
                        StatusCurrentRuntime.builder().coilNo("C001").repeatProdNo(3).build()))
                .build();

        IronLossResult result = algorithm.calculate(input(values, status)).get(0);

        assertThat(result.getCoilNo()).isEqualTo("C001");
        assertThat(result.getHeadLength()).isEqualByComparingTo("0.1");
        assertThat(result.getRepeatProdNo()).isEqualTo(3);
        assertThat(result.getCellCode()).isEqualTo("FCL1001");
        assertThat(result.getParameters()).containsEntry("iron_loss", new BigDecimal("1.234567890123456789"));
        assertThat(result.getReceivedAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void belowThresholdAndMissingRequiredPointsProduceNoResult() {
        assertThat(algorithm.calculate(input(values(new BigDecimal("0.09")), null))).isEmpty();
        Map<String, Object> missingCoil = values(BigDecimal.ONE);
        missingCoil.remove(TRACKING_PREFIX + "coil_no");
        assertThat(algorithm.calculate(input(missingCoil, null))).isEmpty();
        Map<String, Object> invalidLength = values("bad");
        assertThat(algorithm.calculate(input(invalidLength, null))).isEmpty();
    }

    @Test
    void missingConditionStillReadsLengthAndUnmatchedStatusAllowsNullRepeatProdNo() {
        config.getTracking().setStartCondition(null);

        IronLossResult result = algorithm.calculate(input(values(BigDecimal.ZERO), null)).get(0);

        assertThat(result.getHeadLength()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getRepeatProdNo()).isNull();
    }

    @Test
    void candidateRepeatProdNoAndCellCodePointAreUsed() {
        IronLossSegmentConfig segment = config.getSegments().get(0);
        segment.setCellCodeValue(null);
        segment.setCellCodePoint(point("cell_code"));
        Map<String, Object> values = values(BigDecimal.ONE);
        values.put(TECH_PREFIX + "cell_code", "7");
        StatusTrackingContext status = StatusTrackingContext.builder()
                .candidates(Collections.singletonMap("por1",
                        StatusCandidateRuntime.builder().coilNo("C001").repeatProdNo(5).build()))
                .build();

        IronLossResult result = algorithm.calculate(input(values, status)).get(0);

        assertThat(result.getRepeatProdNo()).isEqualTo(5);
        assertThat(result.getCellCode()).isEqualTo("FCL1007");
        values.put(TECH_PREFIX + "cell_code", "1000");
        assertThat(algorithm.calculate(input(values, status)).get(0).getCellCode()).isNull();
    }

    private PointConfig point(String name) {
        return PointConfig.builder().name(name).build();
    }

    private Map<String, Object> values(Object length) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(TRACKING_PREFIX + "coil_no", "C001");
        values.put(TRACKING_PREFIX + "length", length);
        values.put(TECH_PREFIX + "thickness", new BigDecimal("0.45"));
        return values;
    }

    private TrackingInput input(Map<String, Object> values, StatusTrackingContext status) {
        return TrackingInput.builder().unitCode("FCL1").trackingType(TrackingType.IRONLOSS)
                .latestSnapshot(PointSnapshot.builder().receivedAt(RECEIVED_AT).values(values).build())
                .statusContext(status).build();
    }
}
