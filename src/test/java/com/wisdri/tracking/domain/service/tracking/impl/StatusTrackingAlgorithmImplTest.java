package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.tracking.trace.TrackingStepLogger;
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

class StatusTrackingAlgorithmImplTest {
    private final AtomicReference<StatusTrackingRuntime> runtime = new AtomicReference<>();
    private StatusTrackingAlgorithmImpl algorithm;
    private StatusTrackingConfig statusConfig;

    @BeforeEach
    void setUp() {
        TrackingRuntimeRepositoryDispatcher repository = mock(TrackingRuntimeRepositoryDispatcher.class);
        statusConfig = config();
        when(repository.findConfigAs("CP1", TrackingType.STATUS, StatusTrackingConfig.class))
                .thenReturn(Optional.of(statusConfig));
        when(repository.findRuntimeAs("CP1", TrackingType.STATUS, StatusTrackingRuntime.class))
                .thenAnswer(invocation -> Optional.ofNullable(runtime.get()));
        doAnswer(invocation -> {
            runtime.set((StatusTrackingRuntime) invocation.getArgument(0));
            return null;
        }).when(repository).saveRuntime(any(TrackingRuntime.class));

        algorithm = new StatusTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", repository);
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", mock(TrackingStepLogger.class));
    }

    @Test
    void selectsLargestNetChangeForEachSideAfterWindowIsFull() {
        assertEmptySides(calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200",
                "C1", "COIL-C1", "10"));
        assertEmptySides(calculate(true, "U1", "COIL-U1", "102", "U2", "COIL-U2", "194",
                "C1", "COIL-C1", "8"));

        List<StatusResult> results = calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "188",
                "C1", "COIL-C1", "17");

        assertThat(results).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.UNCOILER, DeviceSide.COILER);
        assertThat(results.get(0).getRunning()).isTrue();
        assertThat(results.get(0).getDeviceCode()).isEqualTo("U2");
        assertThat(results.get(0).getCoilNo()).isEqualTo("COIL-U2");
        assertThat(results.get(0).getColorNo()).isEqualTo("U2-COLOR");
        assertThat(results.get(0).getRemainingLength()).isEqualByComparingTo("188");
        assertThat(results.get(1).getRunning()).isTrue();
        assertThat(results.get(1).getDeviceCode()).isEqualTo("C1");
        assertThat(results.get(1).getRemainingLength()).isEqualByComparingTo("17");
        assertThat(runtime.get().getCandidates().get("U2").getLengths())
                .containsExactly(new BigDecimal("200"), new BigDecimal("194"), new BigDecimal("188"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getColorNo())
                .isEqualTo("U2-COLOR");
    }

    @Test
    void usesConfigurationOrderWhenChangesAreEqual() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "98", "U2", "COIL-U2", "198", "C1", "COIL-C1", "12");

        List<StatusResult> results = calculate(true, "U1", "COIL-U1", "94", "U2", "COIL-U2", "194",
                "C1", "COIL-C1", "16");

        assertThat(results.get(0).getDeviceCode()).isEqualTo("U1");
    }

    @Test
    void selectsCandidateWhenWindowRangeReachesThresholdAlthoughEndpointsDoNot() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "2598.77",
                "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "2604.40",
                "C1", "COIL-C1", "10");

        List<StatusResult> results = calculate(true, "U1", "COIL-U1", "100",
                "U2", "COIL-U2", "2598.86", "C1", "COIL-C1", "10");

        assertThat(results.get(0).getRunning()).isTrue();
        assertThat(results.get(0).getDeviceCode()).isEqualTo("U2");
        assertThat(results.get(0).getRemainingLength()).isEqualByComparingTo("2598.86");
    }

    @Test
    void selectsCandidatesRegardlessOfChangeDirection() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "20");
        calculate(true, "U1", "COIL-U1", "103", "U2", "COIL-U2", "198", "C1", "COIL-C1", "17");

        List<StatusResult> results = calculate(true, "U1", "COIL-U1", "106", "U2", "COIL-U2", "196",
                "C1", "COIL-C1", "14");

        assertThat(results.get(0).getRunning()).isTrue();
        assertThat(results.get(0).getDeviceCode()).isEqualTo("U1");
        assertThat(results.get(0).getRemainingLength()).isEqualByComparingTo("106");
        assertThat(results.get(1).getRunning()).isTrue();
        assertThat(results.get(1).getDeviceCode()).isEqualTo("C1");
        assertThat(results.get(1).getRemainingLength()).isEqualByComparingTo("14");
    }

    @Test
    void checksSideSpecificDirectionWhenMonotonicityCheckIsEnabled() {
        statusConfig.getTracking().setMonotonicityCheckEnabled(true);
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "20");
        calculate(true, "U1", "COIL-U1", "103", "U2", "COIL-U2", "202", "C1", "COIL-C1", "17");

        List<StatusResult> results = calculate(true, "U1", "COIL-U1", "106", "U2", "COIL-U2", "204",
                "C1", "COIL-C1", "14");

        assertEmptySides(results);
    }

    @Test
    void resetsCandidateWindowWhenCoilChangesOrLengthIsInvalid() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "195", "C1", "COIL-C1", "15");

        Map<String, Object> values = values(true, "U1", "U1-new", "90", "U2", "COIL-U2", "bad",
                "C1", "COIL-C1", "20");
        List<StatusResult> results = algorithm.calculate(input(values));

        assertThat(results.get(0).getRunning()).isFalse();
        assertThat(runtime.get().getCandidates().get("U1").getCoilNo()).isEqualTo("U1-new");
        assertThat(runtime.get().getCandidates().get("U1").getLengths()).containsExactly(new BigDecimal("90"));
        assertThat(runtime.get().getCandidates()).doesNotContainKey("U2");
    }

    @Test
    void stoppedLineClearsHistoryAndReturnsTwoEmptyStates() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");

        List<StatusResult> results = calculate(false, "U1", "COIL-U1", "90", "U2", "COIL-U2", "190",
                "C1", "COIL-C1", "20");

        assertEmptySides(results);
        assertThat(runtime.get().getStartConditionPointValue()).isEqualByComparingTo("0");
        assertThat(runtime.get().getCandidates()).isEmpty();
        assertThat(runtime.get().getCurrent().values())
                .allMatch(current -> Boolean.FALSE.equals(current.getRunning())
                        && current.getCoilNo() == null && current.getRemainingLength() == null);
    }

    private List<StatusResult> calculate(boolean started, String... values) {
        return algorithm.calculate(input(values(started, values)));
    }

    private Map<String, Object> values(boolean started, String... groups) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("/status/run", started ? BigDecimal.ONE : BigDecimal.ZERO);
        for (int index = 0; index < groups.length; index += 3) {
            String code = groups[index];
            values.put("/status/" + code.toLowerCase() + "_coil", groups[index + 1]);
            values.put("/status/" + code.toLowerCase() + "_color", code + "-COLOR");
            values.put("/status/" + code.toLowerCase() + "_length", groups[index + 2]);
        }
        return values;
    }

    private TrackingInput input(Map<String, Object> values) {
        return TrackingInput.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.STATUS)
                .latestSnapshot(PointSnapshot.builder().values(values).receivedAt(Instant.now()).build())
                .build();
    }

    private StatusTrackingConfig config() {
        return StatusTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.STATUS)
                .tracking(StatusTrackingSection.builder()
                        .pointPrefix("/status/")
                        .startCondition(StartCondition.builder()
                                .point(point("run"))
                                .threshold(BigDecimal.ONE)
                                .build())
                        .sampleCount(3)
                        .minLengthChange(new BigDecimal("4"))
                        .points(Arrays.asList(
                                group("U1", DeviceSide.UNCOILER),
                                group("U2", DeviceSide.UNCOILER),
                                group("C1", DeviceSide.COILER)))
                        .build())
                .build();
    }

    private StatusPointGroup group(String code, DeviceSide side) {
        return StatusPointGroup.builder()
                .code(code)
                .name(code + " device")
                .side(side)
                .coilNo(point(code.toLowerCase() + "_coil"))
                .colorNo(point(code.toLowerCase() + "_color"))
                .remainingLength(point(code.toLowerCase() + "_length"))
                .build();
    }

    private PointConfig point(String name) {
        return PointConfig.builder().name(name).build();
    }

    private void assertEmptySides(List<StatusResult> results) {
        assertThat(results).hasSize(2);
        assertThat(results).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.UNCOILER, DeviceSide.COILER);
        assertThat(results).allMatch(result -> Boolean.FALSE.equals(result.getRunning())
                && result.getDeviceCode() == null && result.getDeviceName() == null
                && result.getCoilNo() == null && result.getRemainingLength() == null);
    }
}
