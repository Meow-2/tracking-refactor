package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodConfig;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodDefinition;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodDefinitions;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
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
import com.wisdri.tracking.domain.repository.quality.QualityRepository;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusTrackingAlgorithmImplTest {
    private final AtomicReference<StatusTrackingRuntime> runtime = new AtomicReference<>();
    private StatusTrackingAlgorithmImpl algorithm;
    private StatusTrackingConfig statusConfig;
    private QualityRepository qualityRepository;

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
        qualityRepository = mock(QualityRepository.class);
        when(qualityRepository.queryProductNo(anyString())).thenReturn(1);
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", repository);
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", mock(TrackingStepLogger.class));
        ReflectionTestUtils.setField(algorithm, "qualityRepository", qualityRepository);
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
        assertThat(current.get(DeviceSide.UNCOILER).getProductNo()).isEqualTo(1);
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
        assertThat(candidate.getCoilNo()).isEmpty();
        assertThat(candidate.getProductNo()).isNull();
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
                        && item.getCoilNo() == null && item.getRemainingLength() == null
                        && item.getMaxLength() == null);
    }

    @Test
    void queriesProductNoOnlyWhenCoilChanges() {
        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "195", "C1", "COIL-C1", "15");

        verify(qualityRepository, times(1)).queryProductNo("COIL-U1");
        assertThat(runtime.get().getCandidates().get("U1").getProductNo()).isEqualTo(1);

        calculate(true, "U1", "COIL-U1-NEW", "90", "U2", "COIL-U2", "190", "C1", "COIL-C1", "20");

        verify(qualityRepository, times(1)).queryProductNo("COIL-U1-NEW");
        assertThat(runtime.get().getCandidates().get("U1").getProductNo()).isEqualTo(1);
    }

    @Test
    void keepsCalculatingWhenProductNoQueryFails() {
        when(qualityRepository.queryProductNo("COIL-U1")).thenThrow(new IllegalStateException("unavailable"));

        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        Map<DeviceSide, StatusCurrentRuntime> current = calculate(
                true, "U1", "COIL-U1", "90", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");

        assertThat(runtime.get().getCandidates().get("U1").getProductNo()).isNull();
        assertThat(current.get(DeviceSide.UNCOILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.UNCOILER).getProductNo()).isNull();
        verify(qualityRepository, times(1)).queryProductNo("COIL-U1");
    }

    @Test
    void keepsCalculatingWhenProductNoQueryReturnsNull() {
        when(qualityRepository.queryProductNo("COIL-U1")).thenReturn(null);

        calculate(true, "U1", "COIL-U1", "100", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        calculate(true, "U1", "COIL-U1", "95", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");
        Map<DeviceSide, StatusCurrentRuntime> current = calculate(
                true, "U1", "COIL-U1", "90", "U2", "COIL-U2", "200", "C1", "COIL-C1", "10");

        assertThat(runtime.get().getCandidates().get("U1").getProductNo()).isNull();
        assertThat(current.get(DeviceSide.UNCOILER).getRunning()).isTrue();
        assertThat(current.get(DeviceSide.UNCOILER).getProductNo()).isNull();
        verify(qualityRepository, times(1)).queryProductNo("COIL-U1");
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
        assertThat(uncoiler.getProductNo()).isEqualTo(1);
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
                && item.getCoilNo() == null && item.getProductNo() == null
                && item.getRemainingLength() == null);
    }
}
