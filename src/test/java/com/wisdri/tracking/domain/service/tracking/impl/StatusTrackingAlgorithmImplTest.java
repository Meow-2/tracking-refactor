package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.process.RollingConfig;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodConfig;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodDefinition;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodDefinitions;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.DevicePosition;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.repository.product.RepeatProdNoRepository;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusTrackingAlgorithmImplTest {
    private final AtomicReference<StatusTrackingRuntime> runtime = new AtomicReference<>();
    private StatusTrackingAlgorithmImpl algorithm;
    private StatusTrackingConfig statusConfig;
    private RepeatProdNoRepository repeatProdNoRepository;
    private TrackingStepLogger trackingStepLogger;

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
        repeatProdNoRepository = mock(RepeatProdNoRepository.class);
        when(repeatProdNoRepository.findLatest(anyString(), anyString())).thenReturn(1);
        when(repeatProdNoRepository.allocateNext(anyString(), anyString())).thenReturn(1);
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", repository);
        trackingStepLogger = mock(TrackingStepLogger.class);
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", trackingStepLogger);
        ReflectionTestUtils.setField(algorithm, "repeatProdNoRepository", repeatProdNoRepository);
    }

    @Test
    void selectsLargestNetChangeForEachSideAfterWindowIsFull() {
        enableCoilerMethods();
        assertEmptySides(calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200",
                "C1", "COIL-C1", "10"));
        assertEmptySides(calculate(true, "U1", "COIL-U1", "102", "U2", "COIL-U2", "194",
                "C1", "COIL-C1", "8"));

        Map<DeviceSide, StatusCurrentRuntime> current = calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "188",
                "C1", "COIL-C1", "17");

        assertThat(current.keySet()).containsExactly(DeviceSide.UNCOILER, DeviceSide.COILER);
        assertThat(current.get(DeviceSide.UNCOILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("U2");
        assertThat(current.get(DeviceSide.UNCOILER).getCoilNo()).isEqualTo("COIL-U2");
        assertThat(current.get(DeviceSide.UNCOILER).getRepeatProdNo()).isEqualTo(1);
        assertThat(current.get(DeviceSide.UNCOILER).getColorNo()).isEqualTo("U2-COLOR");
        assertThat(current.get(DeviceSide.UNCOILER).getCoilerMethod()).isEqualTo("11");
        assertThat(current.get(DeviceSide.UNCOILER).getCoilerMethodName()).isEqualTo("上开卷");
        assertThat(current.get(DeviceSide.UNCOILER).getRemainingLength()).isEqualByComparingTo("188");
        assertThat(current.get(DeviceSide.COILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.COILER).getDeviceCode()).isEqualTo("C1");
        assertThat(current.get(DeviceSide.COILER).getCoilerMethod()).isEqualTo("19");
        assertThat(current.get(DeviceSide.COILER).getCoilerMethodName()).isEqualTo("上卷取");
        assertThat(current.get(DeviceSide.COILER).getRemainingLength()).isEqualByComparingTo("17");
        assertThat(runtime.get().getCandidates().get("U2").getDeviceCode()).isEqualTo("U2");
        assertThat(runtime.get().getCandidates().get("U2").getDeviceName()).isEqualTo("U2 device");
        assertThat(runtime.get().getCandidates().get("U2").getLengths())
                .containsExactly(new BigDecimal("200"), new BigDecimal("194"), new BigDecimal("188"));
        assertThat(runtime.get().getCandidates().get("U2").getMaxLength())
                .isEqualByComparingTo("200");
        assertThat(runtime.get().getCandidates().get("C1").getMaxLength())
                .isEqualByComparingTo("17");
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getColorNo())
                .isEqualTo("U2-COLOR");
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getMaxLength())
                .isEqualByComparingTo("200");
        assertThat(runtime.get().getCurrent().get(DeviceSide.COILER).getMaxLength())
                .isEqualByComparingTo("17");
    }

    @Test
    void swapsSidesAndCreatesNewResultsWhenRollingPassChanges() {
        enableCoilerMethods();
        enableRolling(false);

        List<StatusResult> first = algorithm.calculate(rollingInput(false, 1,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10"));
        assertThat(first).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.UNCOILER, DeviceSide.UNCOILER, DeviceSide.COILER);
        assertThat(first).extracting(StatusResult::getPassNo).containsOnly(1);

        algorithm.calculate(rollingInput(false, 1,
                "U1", "COIL-U1", "95", "U2", "COIL-U2", "195", "C1", "COIL-C1", "15"));
        algorithm.calculate(rollingInput(false, 1,
                "U1", "COIL-U1", "90", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20"));

        List<StatusResult> nextPass = algorithm.calculate(rollingInput(true, 2,
                "U1", "COIL-U1", "90", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20"));

        assertThat(nextPass).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.COILER, DeviceSide.COILER, DeviceSide.UNCOILER);
        assertThat(nextPass).extracting(StatusResult::getPassNo).containsOnly(2);
        assertThat(nextPass.get(0).getCoilerMethodName()).isEqualTo("上卷取");
        assertThat(nextPass.get(2).getCoilerMethodName()).isEqualTo("上开卷");
        assertThat(runtime.get().getRollingDirection()).isTrue();
        assertThat(runtime.get().getPassNo()).isEqualTo(2);
        assertThat(runtime.get().getCandidates().get("U1").getLengths())
                .containsExactly(new BigDecimal("90"));
        assertThat(runtime.get().getCandidates().get("U1").getMaxLength())
                .isEqualByComparingTo("90");
        assertEmptySides(runtime.get().getCurrent());
        assertThat(runtime.get().getCurrent().values()).allMatch(item -> item.getNullCount() == 1);

        algorithm.calculate(rollingInput(true, 2,
                "U1", "COIL-U1", "95", "U2", "COIL-U2", "192", "C1", "COIL-C1", "15"));
        algorithm.calculate(rollingInput(true, 2,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "194", "C1", "COIL-C1", "10"));

        assertThat(runtime.get().getCurrent().get(DeviceSide.COILER).getDeviceCode()).isEqualTo("U1");
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("C1");
    }

    @Test
    void fallsBackToConfiguredThenPreviousSideAndHonorsDirectReverse() {
        enableCoilerMethods();
        enableRolling(true);

        Map<String, Object> firstValues = values(true,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        firstValues.put("/status/pass_no", 1);
        List<StatusResult> first = algorithm.calculate(input(firstValues));
        assertThat(first).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.UNCOILER, DeviceSide.UNCOILER, DeviceSide.COILER);
        assertThat(runtime.get().getRollingDirection()).isNull();

        List<StatusResult> second = algorithm.calculate(rollingInput(false, 2,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10"));
        assertThat(second).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.COILER, DeviceSide.COILER, DeviceSide.UNCOILER);

        Map<String, Object> missingDirection = values(true,
                "U1", "COIL-U1", "105", "U2", "COIL-U2", "205", "C1", "COIL-C1", "5");
        assertThat(algorithm.calculate(input(missingDirection))).isEmpty();
        assertThat(runtime.get().getRollingDirection()).isFalse();
        assertThat(runtime.get().getPassNo()).isEqualTo(2);
    }

    @Test
    void returnsResultsOnlyForChangedCoilsInConfigurationOrder() {
        enableCoilerMethods();

        List<StatusResult> first = algorithm.calculate(input(values(
                true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10")));

        assertThat(first).extracting(StatusResult::getDeviceCode)
                .containsExactly("U1", "U2", "C1");
        assertThat(first).extracting(StatusResult::getDeviceName)
                .containsExactly("U1 device", "U2 device", "C1 device");
        assertThat(first.get(0).getRunning()).isNull();
        assertThat(first.get(0).getTrackingType()).isEqualTo(TrackingType.STATUS);
        assertThat(first.get(0).getCoilNo()).isEqualTo("COIL-U1");
        assertThat(first.get(0).getRemainingLength()).isEqualByComparingTo("100");
        assertThat(first.get(0).getMaxLength()).isEqualByComparingTo("100");

        List<StatusResult> unchanged = algorithm.calculate(input(values(
                true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "195", "C1", "COIL-C1", "15")));
        assertThat(unchanged).isEmpty();

        List<StatusResult> changed = algorithm.calculate(input(values(
                true, "U1", "COIL-U1-NEW", "90", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20")));
        assertThat(changed).singleElement().satisfies(result -> {
            assertThat(result.getDeviceCode()).isEqualTo("U1");
            assertThat(result.getCoilNo()).isEqualTo("COIL-U1-NEW");
        });
    }

    @Test
    void keepsMaximumLengthAfterItLeavesSampleWindowAndResetsItForNewCoil() {
        calculate(true, "U1", "COIL-U1", "120", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "110", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "180", "C1", "COIL-C1", "30");
        calculate(true, "U1", "COIL-U1", "90", "U2", "COIL-U2", "170", "C1", "COIL-C1", "25");

        assertThat(runtime.get().getCandidates().get("U1").getLengths())
                .containsExactly(new BigDecimal("110"), new BigDecimal("100"), new BigDecimal("90"));
        assertThat(runtime.get().getCandidates().get("U1").getMaxLength())
                .isEqualByComparingTo("120");
        assertThat(runtime.get().getCandidates().get("C1").getMaxLength())
                .isEqualByComparingTo("30");

        calculate(true, "U1", "COIL-U1-NEW", "80", "U2", "COIL-U2", "160", "C1", "COIL-C1-NEW", "5");

        assertThat(runtime.get().getCandidates().get("U1").getMaxLength())
                .isEqualByComparingTo("80");
        assertThat(runtime.get().getCandidates().get("C1").getMaxLength())
                .isEqualByComparingTo("5");
    }

    @Test
    void usesConfigurationOrderWhenChangesAreEqual() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "98", "U2", "COIL-U2", "198", "C1", "COIL-C1", "12");

        Map<DeviceSide, StatusCurrentRuntime> current = calculate(true, "U1", "COIL-U1", "94", "U2", "COIL-U2", "194",
                "C1", "COIL-C1", "16");

        assertThat(current.get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("U1");
    }

    @Test
    void selectsCandidateWhenWindowRangeReachesThresholdAlthoughEndpointsDoNot() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "2598.77",
                "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "2604.40",
                "C1", "COIL-C1", "10");

        Map<DeviceSide, StatusCurrentRuntime> current = calculate(true, "U1", "COIL-U1", "100",
                "U2", "COIL-U2", "2598.86", "C1", "COIL-C1", "10");

        assertThat(current.get(DeviceSide.UNCOILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("U2");
        assertThat(current.get(DeviceSide.UNCOILER).getRemainingLength()).isEqualByComparingTo("2598.86");
    }

    @Test
    void selectsCandidatesRegardlessOfChangeDirection() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "20");
        calculate(true, "U1", "COIL-U1", "103", "U2", "COIL-U2", "198", "C1", "COIL-C1", "17");

        Map<DeviceSide, StatusCurrentRuntime> current = calculate(true, "U1", "COIL-U1", "106", "U2", "COIL-U2", "196",
                "C1", "COIL-C1", "14");

        assertThat(current.get(DeviceSide.UNCOILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("U1");
        assertThat(current.get(DeviceSide.UNCOILER).getRemainingLength()).isEqualByComparingTo("106");
        assertThat(current.get(DeviceSide.COILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.COILER).getDeviceCode()).isEqualTo("C1");
        assertThat(current.get(DeviceSide.COILER).getRemainingLength()).isEqualByComparingTo("14");
    }

    @Test
    void checksSideSpecificDirectionWhenMonotonicityCheckIsEnabled() {
        statusConfig.getTracking().setMonotonicityCheckEnabled(true);
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "20");
        calculate(true, "U1", "COIL-U1", "103", "U2", "COIL-U2", "202", "C1", "COIL-C1", "17");

        Map<DeviceSide, StatusCurrentRuntime> current = calculate(true, "U1", "COIL-U1", "106", "U2", "COIL-U2", "204",
                "C1", "COIL-C1", "14");

        assertEmptySides(current);
    }

    @Test
    void retainsEveryConfiguredCandidateButDoesNotSelectIncompleteData() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "195", "C1", "COIL-C1", "15");

        Map<String, Object> values = values(true, "U1", "U1-new", "90", "U2", "COIL-U2", "bad",
                "C1", "COIL-C1", "20");
        algorithm.calculate(input(values));

        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getRunning()).isFalse();
        assertThat(runtime.get().getCandidates().get("U1").getCoilNo()).isEqualTo("U1-new");
        assertThat(runtime.get().getCandidates().get("U1").getLengths()).containsExactly(new BigDecimal("90"));
        assertThat(runtime.get().getCandidates().get("U1").getMaxLength()).isEqualByComparingTo("90");
        assertThat(runtime.get().getCandidates()).containsKeys("U1", "U2", "C1");
        assertThat(runtime.get().getCandidates().get("U2").getDataComplete()).isFalse();
        assertThat(runtime.get().getCandidates().get("U2").getLengths())
                .containsExactly(new BigDecimal("200"), new BigDecimal("195"));
        assertThat(runtime.get().getCandidates().get("U2").getMaxLength())
                .isEqualByComparingTo("200");
    }

    @Test
    void retainsCandidateWithMissingCoilNumberAndClearsItsAnonymousLengthWindow() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "195", "C1", "COIL-C1", "15");

        Map<String, Object> values = values(true, "U1", "", "90", "U2", "COIL-U2", "190",
                "C1", "COIL-C1", "20");
        algorithm.calculate(input(values));

        StatusCandidateRuntime candidate = runtime.get().getCandidates().get("U1");
        assertThat(runtime.get().getCandidates()).containsKeys("U1", "U2", "C1");
        assertThat(candidate.getDataComplete()).isFalse();
        assertThat(candidate.getCoilNo()).isNull();
        assertThat(candidate.getRepeatProdNo()).isNull();
        assertThat(candidate.getLengths()).isEmpty();
        assertThat(candidate.getMaxLength()).isNull();
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("U2");
    }

    @Test
    void stoppedLineClearsHistoryAndReturnsTwoEmptyStates() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");

        Map<DeviceSide, StatusCurrentRuntime> current = calculate(false, "U1", "COIL-U1", "90", "U2", "COIL-U2", "190",
                "C1", "COIL-C1", "20");

        assertEmptySides(current);
        assertThat(runtime.get().getStartConditionPointValue()).isEqualByComparingTo("0");
        assertThat(runtime.get().getCandidates()).isEmpty();
        assertThat(runtime.get().getCurrent().values())
                .allMatch(item -> Boolean.FALSE.equals(item.getRunning())
                        && item.getNullCount() == 1
                        && item.getCoilNo() == null && item.getRemainingLength() == null
                        && item.getMaxLength() == null);
    }

    @Test
    void retainsUnselectedCurrentUntilCountExceedsConfiguredThreshold() {
        statusConfig.getTracking().setSampleCount(2);
        statusConfig.getTracking().setCurrentClearThreshold(3);
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        Map<DeviceSide, StatusCurrentRuntime> selected = calculate(true,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");
        assertThat(selected.get(DeviceSide.UNCOILER).getNullCount()).isEqualTo(1);
        assertThat(selected.get(DeviceSide.COILER).getNullCount()).isEqualTo(1);

        Map<DeviceSide, StatusCurrentRuntime> retainedAtTwo = calculate(true,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");
        assertRetainedCurrent(retainedAtTwo.get(DeviceSide.UNCOILER), "U2", "COIL-U2", "190", 2);
        assertRetainedCurrent(retainedAtTwo.get(DeviceSide.COILER), "C1", "COIL-C1", "20", 2);

        Map<DeviceSide, StatusCurrentRuntime> retainedAtThree = calculate(true,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");
        assertThat(retainedAtThree.values()).allMatch(item -> item.getNullCount() == 3
                && Boolean.TRUE.equals(item.getRunning()));

        Map<DeviceSide, StatusCurrentRuntime> clearedAtFour = calculate(true,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");
        assertThat(clearedAtFour.values()).allMatch(item -> item.getNullCount() == 1
                && Boolean.FALSE.equals(item.getRunning()) && item.getDeviceCode() == null
                && item.getCoilNo() == null && item.getRemainingLength() == null);

        Map<DeviceSide, StatusCurrentRuntime> emptyAtTwo = calculate(true,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");
        assertThat(emptyAtTwo.values()).allMatch(item -> item.getNullCount() == 2
                && Boolean.FALSE.equals(item.getRunning()) && item.getDeviceCode() == null);
    }

    @Test
    void updatesCurrentCountsIndependentlyForEachSide() {
        statusConfig.getTracking().setSampleCount(2);
        statusConfig.getTracking().setCurrentClearThreshold(3);
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");

        Map<DeviceSide, StatusCurrentRuntime> current = calculate(true,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "180", "C1", "COIL-C1", "20");

        assertRetainedCurrent(current.get(DeviceSide.UNCOILER), "U2", "COIL-U2", "180", 1);
        assertRetainedCurrent(current.get(DeviceSide.COILER), "C1", "COIL-C1", "20", 2);
    }

    @Test
    void treatsMissingCountAsOneAndSaturatesCountAtIntegerMaximum() {
        statusConfig.getTracking().setSampleCount(2);
        statusConfig.getTracking().setCurrentClearThreshold(3);
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");
        runtime.get().getCurrent().get(DeviceSide.UNCOILER).setNullCount(Integer.MAX_VALUE);
        runtime.get().getCurrent().get(DeviceSide.COILER).setNullCount(null);

        Map<DeviceSide, StatusCurrentRuntime> current = calculate(true,
                "U1", "COIL-U1", "100", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");

        assertThat(current.get(DeviceSide.UNCOILER).getNullCount()).isEqualTo(1);
        assertThat(current.get(DeviceSide.UNCOILER).getRunning()).isFalse();
        assertRetainedCurrent(current.get(DeviceSide.COILER), "C1", "COIL-C1", "20", 2);
    }

    @Test
    void queriesRepeatProdNoOnlyWhenCoilChanges() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "195", "C1", "COIL-C1", "15");

        verify(repeatProdNoRepository, times(1)).findLatest("CP1", "COIL-U1");
        assertThat(runtime.get().getCandidates().get("U1").getRepeatProdNo()).isEqualTo(1);

        calculate(true, "U1", "COIL-U1-NEW", "90", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");

        verify(repeatProdNoRepository, times(1)).findLatest("CP1", "COIL-U1-NEW");
        assertThat(runtime.get().getCandidates().get("U1").getRepeatProdNo()).isEqualTo(1);
    }

    @Test
    void cacheKeepsCoilOnLineAndClearsAfterTwoCompleteAbsentFrames() {
        statusConfig.getTracking().setCachePoints(Arrays.asList(point("line_coil")));
        Map<String, Object> onDevice = cacheFrame("COIL-A", null);
        calculateWithPrevious(null, onDevice);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getRepeatProdNo()).isEqualTo(1);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getColorNo()).isEqualTo("U1-COLOR");

        Map<String, Object> onLine = cacheFrame(null, "COIL-A");
        calculateWithPrevious(onDevice, onLine);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getRepeatProdNo()).isEqualTo(1);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getColorNo()).isEqualTo("U1-COLOR");

        Map<String, Object> firstAbsent = cacheFrame(null, null);
        calculateWithPrevious(onLine, firstAbsent);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getRepeatProdNo()).isEqualTo(1);

        Map<String, Object> secondAbsent = cacheFrame(null, null);
        calculateWithPrevious(firstAbsent, secondAbsent);
        assertThat(runtime.get().getCoilCache()).doesNotContainKey("COIL-A");
    }

    @Test
    void cacheIgnoresMissingPointsAndDoesNotClearForStoppedLine() {
        statusConfig.getTracking().setCachePoints(Arrays.asList(point("line_coil")));
        Map<String, Object> onDevice = cacheFrame("COIL-A", null);
        calculateWithPrevious(null, onDevice);

        Map<String, Object> stoppedOnLine = cacheFrame(null, "COIL-A");
        stoppedOnLine.put("/status/run", BigDecimal.ZERO);
        calculateWithPrevious(onDevice, stoppedOnLine);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getColorNo()).isEqualTo("U1-COLOR");

        Map<String, Object> missingPoint = cacheFrame(null, null);
        missingPoint.remove("/status/line_coil");
        calculateWithPrevious(stoppedOnLine, missingPoint);
        Map<String, Object> absent = cacheFrame(null, null);
        calculateWithPrevious(missingPoint, absent);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getRepeatProdNo()).isEqualTo(1);
        calculateWithPrevious(absent, cacheFrame(null, null));
        assertThat(runtime.get().getCoilCache()).doesNotContainKey("COIL-A");
    }

    @Test
    void cacheUsesLatestPorAllocationAndClearsOnlyOldCountWhenAllocationFails() {
        statusConfig.getTracking().setPoints(Arrays.asList(group("por1", DeviceSide.UNCOILER)));
        statusConfig.getTracking().setCachePoints(Arrays.asList(point("line_coil")));
        when(repeatProdNoRepository.allocateNext("CP1", "COIL-A"))
                .thenReturn(1, 2)
                .thenThrow(new IllegalStateException("pg unavailable"));

        Map<String, Object> onDevice = values(true, "por1", "COIL-A", "100");
        onDevice.put("/status/line_coil", null);
        calculateWithPrevious(null, onDevice);
        Map<String, Object> onLine = values(true, "por1", "", "0");
        onLine.put("/status/line_coil", "COIL-A");
        calculateWithPrevious(onDevice, onLine);
        calculateWithPrevious(onLine, onDevice);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getRepeatProdNo()).isEqualTo(2);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getColorNo()).isEqualTo("por1-COLOR");

        calculateWithPrevious(onDevice, onLine);
        Map<String, Object> failedDevice = values(true, "por1", "COIL-A", "100");
        failedDevice.put("/status/line_coil", null);
        failedDevice.put("/status/por1_color", "NEW-COLOR");
        calculateWithPrevious(onLine, failedDevice);
        assertThat(runtime.get().getCoilCache().get("COIL-A").getRepeatProdNo()).isNull();
        assertThat(runtime.get().getCoilCache().get("COIL-A").getColorNo()).isEqualTo("NEW-COLOR");
    }

    @Test
    void nullAndEmptyCachePointsPreserveLegacyBehavior() {
        algorithm.calculate(input(cacheFrame("COIL-A", null)));
        assertThat(runtime.get().getCoilCache()).isEmpty();
        statusConfig.getTracking().setCachePoints(Arrays.asList());
        algorithm.calculate(input(cacheFrame("COIL-A", null)));
        assertThat(runtime.get().getCoilCache()).isEmpty();
    }

    @Test
    void allocatesPorBeforeReadingOtherDevicesAndKeepsConfiguredResultOrder() {
        statusConfig.getTracking().setPoints(Arrays.asList(
                group("tr1", DeviceSide.COILER), group("por1", DeviceSide.UNCOILER)));
        enableCoilerMethods();
        when(repeatProdNoRepository.allocateNext("CP1", "COIL-A")).thenReturn(2);
        when(repeatProdNoRepository.findLatest("CP1", "COIL-A")).thenReturn(2);

        List<StatusResult> results = algorithm.calculate(input(values(true,
                "tr1", "COIL-A", "10", "por1", "COIL-A", "100")));

        InOrder calls = inOrder(repeatProdNoRepository);
        calls.verify(repeatProdNoRepository).allocateNext("CP1", "COIL-A");
        calls.verify(repeatProdNoRepository).findLatest("CP1", "COIL-A");
        assertThat(runtime.get().getCandidates().keySet()).containsExactly("tr1", "por1");
        assertThat(results).extracting(StatusResult::getDeviceCode).containsExactly("tr1", "por1");
        assertThat(results).extracting(StatusResult::getRepeatProdNo).containsExactly(2, 2);

        algorithm.calculate(input(values(true,
                "tr1", "COIL-A", "15", "por1", "COIL-A", "95")));
        verify(repeatProdNoRepository, times(1)).allocateNext("CP1", "COIL-A");
        verify(repeatProdNoRepository, times(1)).findLatest("CP1", "COIL-A");

        when(repeatProdNoRepository.allocateNext("CP1", "COIL-B")).thenReturn(3);
        when(repeatProdNoRepository.findLatest("CP1", "COIL-B")).thenReturn(3);
        List<StatusResult> next = algorithm.calculate(input(values(true,
                "tr1", "COIL-B", "20", "por1", "COIL-B", "100")));

        assertThat(next).extracting(StatusResult::getRepeatProdNo).containsExactly(3, 3);
        verify(repeatProdNoRepository, times(1)).allocateNext("CP1", "COIL-B");
        verify(repeatProdNoRepository, times(1)).findLatest("CP1", "COIL-B");
    }

    @Test
    void porAllocationFailureLeavesRepeatProdNoEmptyWithoutStoppingStatus() {
        statusConfig.getTracking().setPoints(Arrays.asList(group("por1", DeviceSide.UNCOILER)));
        when(repeatProdNoRepository.allocateNext("CP1", "COIL-A"))
                .thenThrow(new IllegalStateException("pg unavailable"));

        algorithm.calculate(input(values(true, "por1", "COIL-A", "100")));

        assertThat(runtime.get().getCandidates().get("por1").getRepeatProdNo()).isNull();
        verify(repeatProdNoRepository, times(1)).allocateNext("CP1", "COIL-A");
    }

    @Test
    void ignoresDotOnlyCoilNumber() {
        enableCoilerMethods();

        List<StatusResult> results = algorithm.calculate(input(values(true,
                "U1", "..................", "100",
                "U2", "COIL-U2", "200",
                "C1", "COIL-C1", "10")));

        StatusCandidateRuntime candidate = runtime.get().getCandidates().get("U1");
        assertThat(candidate.getCoilNo()).isNull();
        assertThat(candidate.getRepeatProdNo()).isNull();
        assertThat(candidate.getDataComplete()).isFalse();
        assertThat(candidate.getLengths()).isEmpty();
        assertThat(candidate.getMaxLength()).isNull();
        assertThat(results).extracting(StatusResult::getDeviceCode)
                .containsExactly("U2", "C1");
        verify(repeatProdNoRepository, never()).findLatest("CP1", "..................");
    }

    @Test
    void ignoresRequestColorPlaceholderCoilNumber() {
        enableCoilerMethods();

        String placeholder = " request   COLOR no. 10 ";
        List<StatusResult> results = algorithm.calculate(input(values(true,
                "U1", placeholder, "100",
                "U2", "COIL-U2", "200",
                "C1", "COIL-C1", "10")));

        StatusCandidateRuntime candidate = runtime.get().getCandidates().get("U1");
        assertThat(candidate.getCoilNo()).isNull();
        assertThat(candidate.getRepeatProdNo()).isNull();
        assertThat(candidate.getDataComplete()).isFalse();
        assertThat(candidate.getLengths()).isEmpty();
        assertThat(candidate.getMaxLength()).isNull();
        assertThat(results).extracting(StatusResult::getDeviceCode)
                .containsExactly("U2", "C1");
        verify(repeatProdNoRepository, never()).findLatest("CP1", placeholder.trim());
    }

    @Test
    void keepsCalculatingWhenRepeatProdNoQueryFails() {
        when(repeatProdNoRepository.findLatest("CP1", "COIL-U1"))
                .thenThrow(new IllegalStateException("unavailable"));

        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        Map<DeviceSide, StatusCurrentRuntime> current = calculate(
                true, "U1", "COIL-U1", "90", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");

        assertThat(runtime.get().getCandidates().get("U1").getRepeatProdNo()).isNull();
        assertThat(current.get(DeviceSide.UNCOILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.UNCOILER).getRepeatProdNo()).isNull();
        verify(repeatProdNoRepository, times(1)).findLatest("CP1", "COIL-U1");
    }

    @Test
    void keepsCalculatingWhenRepeatProdNoQueryReturnsNull() {
        when(repeatProdNoRepository.findLatest("CP1", "COIL-U1")).thenReturn(null);

        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        Map<DeviceSide, StatusCurrentRuntime> current = calculate(
                true, "U1", "COIL-U1", "90", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");

        assertThat(runtime.get().getCandidates().get("U1").getRepeatProdNo()).isNull();
        assertThat(current.get(DeviceSide.UNCOILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.UNCOILER).getRepeatProdNo()).isNull();
        verify(repeatProdNoRepository, times(1)).findLatest("CP1", "COIL-U1");
    }

    @Test
    void freezesCoilerMethodForNewCoilAndUsesPointValueBeforeDefault() {
        enableCoilerMethods();
        statusConfig.getTracking().getPoints().get(0).setCoilerMethod(CoilerMethodConfig.builder()
                .name("u1_method").type(PointDataType.BOOLEAN)
                .defaultValue(false).falseIndex(0).build());
        Map<String, Object> first = values(true, "U1", "COIL-U1", "100",
                "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        first.put("/status/u1_method", true);

        List<StatusResult> results = algorithm.calculate(input(first));

        StatusResult uncoiler = results.stream()
                .filter(result -> "U1".equals(result.getDeviceCode())).findFirst().get();
        assertThat(uncoiler.getCoilNo()).isEqualTo("COIL-U1");
        assertThat(uncoiler.getRepeatProdNo()).isEqualTo(1);
        assertThat(uncoiler.getCoilerMethod()).isEqualTo("91");
        assertThat(uncoiler.getCoilerMethodName()).isEqualTo("下开卷");
        assertThat(uncoiler.getMaxLength()).isEqualByComparingTo("100");
        StatusResult coiler = results.stream()
                .filter(result -> "C1".equals(result.getDeviceCode())).findFirst().get();
        assertThat(coiler.getCoilerMethod()).isEqualTo("19");
        assertThat(coiler.getCoilerMethodName()).isEqualTo("上卷取");

        first.put("/status/u1_method", false);
        assertThat(algorithm.calculate(input(first))).isEmpty();
        assertThat(runtime.get().getCandidates().get("U1").getCoilerMethod()).isEqualTo("91");
        assertThat(runtime.get().getCandidates().get("U1").getCoilerMethodName()).isEqualTo("下开卷");
    }

    @Test
    void fallsBackToDefaultWhenConfiguredMethodPointValueIsInvalid() {
        enableCoilerMethods();
        statusConfig.getTracking().getPoints().get(0).setCoilerMethod(CoilerMethodConfig.builder()
                .name("u1_method").type(PointDataType.BOOLEAN)
                .defaultValue(false).falseIndex(0).build());
        Map<String, Object> first = values(true, "U1", "COIL-U1", "100",
                "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        first.put("/status/u1_method", "unknown");

        List<StatusResult> results = algorithm.calculate(input(first));

        StatusResult uncoiler = results.stream()
                .filter(result -> "U1".equals(result.getDeviceCode())).findFirst().get();
        assertThat(uncoiler.getCoilerMethod()).isEqualTo("11");
        assertThat(uncoiler.getCoilerMethodName()).isEqualTo("上开卷");
    }

    private void enableCoilerMethods() {
        statusConfig.getTracking().setCoilerMethodDef(CoilerMethodDefinitions.builder()
                .uncoiler(CoilerMethodDefinition.builder()
                        .name(Arrays.asList("上开卷", "下开卷"))
                        .code(Arrays.asList("11", "91")).build())
                .coiler(CoilerMethodDefinition.builder()
                        .name(Arrays.asList("上卷取", "下卷取"))
                        .code(Arrays.asList("19", "99")).build())
                .build());
        for (StatusPointGroup group : statusConfig.getTracking().getPoints()) {
            group.setCoilerMethod(CoilerMethodConfig.builder()
                    .defaultValue(false).falseIndex(0).build());
        }
    }

    private void enableRolling(boolean directReverse) {
        statusConfig.getTracking().setRolling(RollingConfig.builder()
                .directPoint(point("rolling_direction"))
                .passNoPoint(point("pass_no"))
                .directReverse(directReverse)
                .build());
    }

    @Test
    void positionModeSelectsFirstPassAndBothLaterDirections() {
        enablePositionMode();

        List<StatusResult> first = algorithm.calculate(rollingInput(false, 1,
                "por1", "COIL-A", "100", "tr1", "COIL-B", "0", "tr2", "COIL-A", "5"));
        assertThat(first).extracting(StatusResult::getDeviceCode).containsExactly("por1", "tr2");
        assertThat(first).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.UNCOILER, DeviceSide.COILER);
        assertThat(first.get(0).getCoilerMethod()).isEqualTo("11");
        assertThat(first.get(1).getCoilerMethod()).isEqualTo("19");
        algorithm.calculate(rollingInput(false, 1,
                "por1", "COIL-A", "90", "tr1", "COIL-B", "0", "tr2", "COIL-A", "15"));
        algorithm.calculate(rollingInput(false, 1,
                "por1", "COIL-A", "80", "tr1", "COIL-B", "0", "tr2", "COIL-A", "25"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("por1");
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getMaxLength())
                .isEqualByComparingTo("100");
        assertThat(runtime.get().getCurrent().get(DeviceSide.COILER).getDeviceCode()).isEqualTo("tr2");

        List<StatusResult> second = algorithm.calculate(rollingInput(true, 2,
                "por1", "COIL-A", "80", "tr1", "COIL-A", "5", "tr2", "COIL-A", "25"));
        assertThat(second).extracting(StatusResult::getDeviceCode).containsExactly("tr1", "tr2");
        assertThat(second).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.COILER, DeviceSide.UNCOILER);
        assertEmptySides(runtime.get().getCurrent());
        algorithm.calculate(rollingInput(true, 2,
                "por1", "COIL-A", "80", "tr1", "COIL-A", "15", "tr2", "COIL-A", "15"));
        algorithm.calculate(rollingInput(true, 2,
                "por1", "COIL-A", "80", "tr1", "COIL-A", "25", "tr2", "COIL-A", "5"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("tr2");
        assertThat(runtime.get().getCurrent().get(DeviceSide.COILER).getDeviceCode()).isEqualTo("tr1");

        List<StatusResult> third = algorithm.calculate(rollingInput(false, 3,
                "por1", "COIL-A", "80", "tr1", "COIL-A", "25", "tr2", "COIL-A", "5"));
        assertThat(third).extracting(StatusResult::getDeviceCode).containsExactly("tr1", "tr2");
        assertEmptySides(runtime.get().getCurrent());
        algorithm.calculate(rollingInput(false, 3,
                "por1", "COIL-A", "80", "tr1", "COIL-A", "15", "tr2", "COIL-A", "15"));
        algorithm.calculate(rollingInput(false, 3,
                "por1", "COIL-A", "80", "tr1", "COIL-A", "5", "tr2", "COIL-A", "25"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("tr1");
        assertThat(runtime.get().getCurrent().get(DeviceSide.COILER).getDeviceCode()).isEqualTo("tr2");
    }

    @Test
    void positionModeLogsMissingSourceDeviceAndMissingPass() {
        enablePositionMode();
        algorithm.calculate(rollingInput(false, 1,
                "por1", "COIL-A", "100", "tr1", "COIL-B", "0", "tr2", "COIL-A", "5"));
        algorithm.calculate(rollingInput(false, 1,
                "por1", "COIL-A", "90", "tr1", "COIL-B", "0", "tr2", "COIL-A", "15"));
        algorithm.calculate(rollingInput(false, 1,
                "por1", "COIL-A", "80", "tr1", "COIL-B", "0", "tr2", "COIL-A", "25"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("por1");
        assertThat(algorithm.calculate(rollingInput(true, 1,
                "por1", "COIL-A", "70", "tr1", "COIL-B", "0", "tr2", "COIL-A", "35"))).isEmpty();
        assertEmptySides(runtime.get().getCurrent());
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> details = ArgumentCaptor.forClass(Map.class);
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("轧制设备选择无效"), details.capture());
        assertThat(details.getValue().get("reason")).isEqualTo("该方向缺少开卷或卷取设备配置");
        assertThat(details.getValue().get("uncoilerConfigured")).isEqualTo(false);
        assertThat(details.getValue().get("coilerConfigured")).isEqualTo(true);
        Map<String, Object> missingPass = values(true,
                "por1", "COIL-A", "80", "tr1", "COIL-B", "0", "tr2", "COIL-A", "25");
        missingPass.put("/status/rolling_direction", false);
        runtime.set(null);
        assertThat(algorithm.calculate(input(missingPass))).isEmpty();
        assertEmptySides(runtime.get().getCurrent());
    }

    @Test
    void positionModeCanStartFirstPassFromLeftPor() {
        statusConfig.getTracking().setPoints(Arrays.asList(
                positionedGroup("por2", DeviceSide.UNCOILER, DevicePosition.LEFT),
                positionedGroup("tr1", DeviceSide.COILER, DevicePosition.RIGHT),
                positionedGroup("tr2", DeviceSide.COILER, DevicePosition.LEFT)));
        enableCoilerMethods();
        enableRolling(false);

        List<StatusResult> first = algorithm.calculate(rollingInput(true, 1,
                "por2", "COIL-A", "100", "tr1", "COIL-A", "5", "tr2", "COIL-B", "0"));
        assertThat(first).extracting(StatusResult::getDeviceCode).containsExactly("por2", "tr1");
        assertThat(first).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.UNCOILER, DeviceSide.COILER);
        algorithm.calculate(rollingInput(true, 1,
                "por2", "COIL-A", "90", "tr1", "COIL-A", "15", "tr2", "COIL-B", "0"));
        algorithm.calculate(rollingInput(true, 1,
                "por2", "COIL-A", "80", "tr1", "COIL-A", "25", "tr2", "COIL-B", "0"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("por2");
        assertThat(runtime.get().getCurrent().get(DeviceSide.COILER).getDeviceCode()).isEqualTo("tr1");
    }

    @Test
    void positionModeHonorsReversedDirectionPoint() {
        enablePositionMode();
        statusConfig.getTracking().getRolling().setDirectReverse(true);

        List<StatusResult> first = algorithm.calculate(rollingInput(true, 1,
                "por1", "COIL-A", "100", "tr1", "COIL-B", "0", "tr2", "COIL-A", "5"));
        assertThat(first).extracting(StatusResult::getDeviceCode).containsExactly("por1", "tr2");
        algorithm.calculate(rollingInput(true, 1,
                "por1", "COIL-A", "90", "tr1", "COIL-B", "0", "tr2", "COIL-A", "15"));
        algorithm.calculate(rollingInput(true, 1,
                "por1", "COIL-A", "80", "tr1", "COIL-B", "0", "tr2", "COIL-A", "25"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("por1");

        assertThat(algorithm.calculate(rollingInput(false, 1,
                "por1", "COIL-A", "70", "tr1", "COIL-B", "0", "tr2", "COIL-A", "35"))).isEmpty();
        assertEmptySides(runtime.get().getCurrent());

        List<StatusResult> later = algorithm.calculate(rollingInput(false, 2,
                "por1", "COIL-A", "70", "tr1", "COIL-A", "5", "tr2", "COIL-A", "35"));
        assertThat(later).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.COILER, DeviceSide.UNCOILER);
    }

    @Test
    void changingDirectReverseResetsPositionSelection() {
        enablePositionMode();
        algorithm.calculate(rollingInput(false, 2,
                "por1", "COIL-A", "100", "tr1", "COIL-A", "80", "tr2", "COIL-A", "5"));
        algorithm.calculate(rollingInput(false, 2,
                "por1", "COIL-A", "100", "tr1", "COIL-A", "70", "tr2", "COIL-A", "15"));
        algorithm.calculate(rollingInput(false, 2,
                "por1", "COIL-A", "100", "tr1", "COIL-A", "60", "tr2", "COIL-A", "25"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("tr1");

        statusConfig.getTracking().getRolling().setDirectReverse(true);
        List<StatusResult> reversed = algorithm.calculate(rollingInput(false, 2,
                "por1", "COIL-A", "100", "tr1", "COIL-A", "60", "tr2", "COIL-A", "25"));
        assertThat(reversed).extracting(StatusResult::getSide)
                .containsExactly(DeviceSide.COILER, DeviceSide.UNCOILER);
        assertEmptySides(runtime.get().getCurrent());
        algorithm.calculate(rollingInput(false, 2,
                "por1", "COIL-A", "100", "tr1", "COIL-A", "70", "tr2", "COIL-A", "15"));
        algorithm.calculate(rollingInput(false, 2,
                "por1", "COIL-A", "100", "tr1", "COIL-A", "80", "tr2", "COIL-A", "5"));
        assertThat(runtime.get().getCurrent().get(DeviceSide.UNCOILER).getDeviceCode()).isEqualTo("tr2");
        assertThat(runtime.get().getCurrent().get(DeviceSide.COILER).getDeviceCode()).isEqualTo("tr1");
    }

    private void enablePositionMode() {
        statusConfig.getTracking().setPoints(Arrays.asList(
                positionedGroup("por1", DeviceSide.UNCOILER, DevicePosition.RIGHT),
                positionedGroup("tr1", DeviceSide.COILER, DevicePosition.RIGHT),
                positionedGroup("tr2", DeviceSide.COILER, DevicePosition.LEFT)));
        enableCoilerMethods();
        enableRolling(false);
    }

    private StatusPointGroup positionedGroup(String code, DeviceSide side, DevicePosition position) {
        StatusPointGroup group = group(code, side);
        group.setPosition(position);
        return group;
    }

    private TrackingInput rollingInput(boolean direction, int passNo, String... groups) {
        Map<String, Object> values = values(true, groups);
        values.put("/status/rolling_direction", direction);
        values.put("/status/pass_no", passNo);
        return input(values);
    }

    private Map<DeviceSide, StatusCurrentRuntime> calculate(boolean started, String... values) {
        algorithm.calculate(input(values(started, values)));
        return runtime.get().getCurrent();
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

    private void calculateWithPrevious(Map<String, Object> previous, Map<String, Object> latest) {
        TrackingInput input = input(latest);
        if (previous != null) {
            input.setPreviousSnapshot(PointSnapshot.builder().values(previous)
                    .receivedAt(Instant.now()).build());
        }
        algorithm.calculate(input);
    }

    private Map<String, Object> cacheFrame(String deviceCoil, String lineCoil) {
        Map<String, Object> frame = values(true, "U1", deviceCoil == null ? "" : deviceCoil, "100",
                "U2", "", "0", "C1", "", "0");
        frame.put("/status/line_coil", lineCoil);
        return frame;
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

    private void assertEmptySides(Map<DeviceSide, StatusCurrentRuntime> current) {
        assertThat(current.keySet()).containsExactly(DeviceSide.UNCOILER, DeviceSide.COILER);
        assertThat(current.values()).allMatch(item -> Boolean.FALSE.equals(item.getRunning())
                && item.getDeviceCode() == null && item.getDeviceName() == null
                && item.getCoilNo() == null && item.getRepeatProdNo() == null
                && item.getRemainingLength() == null);
    }

    private void assertRetainedCurrent(StatusCurrentRuntime current,
                                       String deviceCode,
                                       String coilNo,
                                       String remainingLength,
                                       int nullCount) {
        assertThat(current.getRunning()).isTrue();
        assertThat(current.getNullCount()).isEqualTo(nullCount);
        assertThat(current.getDeviceCode()).isEqualTo(deviceCode);
        assertThat(current.getCoilNo()).isEqualTo(coilNo);
        assertThat(current.getRemainingLength()).isEqualByComparingTo(remainingLength);
    }
}
