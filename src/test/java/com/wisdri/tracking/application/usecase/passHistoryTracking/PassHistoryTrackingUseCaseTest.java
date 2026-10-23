package com.wisdri.tracking.application.usecase.passHistoryTracking;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.wisdri.tracking.domain.model.config.*;
import com.wisdri.tracking.domain.model.config.status.*;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.product.RepeatProdNoRepository;
import com.wisdri.tracking.domain.service.tracking.status.StatusDeviceResolver;
import com.wisdri.tracking.infrastructure.dto.history.*;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.history.*;
import com.wisdri.tracking.infrastructure.service.feign.gateway.CubeApiGateway;
import com.wisdri.tracking.infrastructure.service.feign.gateway.QualityServiceGateway;
import com.wisdri.tracking.infrastructure.service.feign.gateway.timeseries.history.TimeSeriesHistoryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PassHistoryTrackingUseCaseTest {
    private final PassHistoryTrackingUseCase useCase = new PassHistoryTrackingUseCase();
    private final CubeApiGateway cube = mock(CubeApiGateway.class);
    private final TimeSeriesHistoryGateway history = mock(TimeSeriesHistoryGateway.class);
    private final RepeatProdNoRepository repeats = mock(RepeatProdNoRepository.class);
    private final QualityServiceGateway quality = mock(QualityServiceGateway.class);
    private StatusTrackingSection tracking;
    private final LocalDateTime start = LocalDateTime.parse("2026-10-09T00:00:00");
    private final LocalDateTime end = start.plusSeconds(60);
    private String coilAtEnd = "A";
    private String coilAtStart = " \u200bA ";
    private int lagSeconds;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(useCase, "cubeApiGateway", cube);
        ReflectionTestUtils.setField(useCase, "historyGateway", history);
        ReflectionTestUtils.setField(useCase, "repeatProdNoRepository", repeats);
        ReflectionTestUtils.setField(useCase, "qualityServiceGateway", quality);
        tracking = StatusTrackingSection.builder().pointPrefix("/zrm1/")
                .startCondition(StartCondition.builder().point(config("speed")).build())
                .rolling(StatusRollingConfig.builder().qualityOutputEnabled(true).qualityMinSpeed(new BigDecimal("0.5"))
                        .passNoPoint(config("pass")).directPoint(config("direction")).outThicknessPoint(config("thickness")).build())
                .points(Arrays.asList(group("por1", DevicePosition.RIGHT), group("tr1", DevicePosition.RIGHT),
                        group("tr2", DevicePosition.LEFT))).build();
        Map<String, PointHistoryMetadata> points = new LinkedHashMap<>();
        for (String field : Arrays.asList("speed", "pass", "direction", "thickness", "por1_coil", "tr1_coil",
                "tr2_coil", "por1_length", "tr1_length", "tr2_length")) {
            points.put("/zrm1/" + field, PointHistoryMetadata.builder().database("cube").objectName("plc")
                    .code(field).path("/zrm1/" + field).build());
        }
        when(cube.fetchHistoryMetadata("zrm1", TrackingType.STATUS)).thenReturn(new TrackingHistoryMetadata(
                StatusTrackingConfig.builder().tracking(tracking).build(), points));
        when(history.queryStates(any())).thenReturn(Arrays.asList(state(1, "2026-10-08 23:59:00", "2026-10-09 00:00:09.123456789"),
                state(2, "2026-10-09 00:00:10", "2026-10-09 00:00:59")));
        Bounds bounds = new Bounds();
        bounds.setFirstTime("2026-10-08 23:59:01");
        bounds.setLastTime("2026-10-09 00:00:09.123456789");
        when(history.queryBounds(any())).thenReturn(bounds);
        when(repeats.findLatestBefore(anyString(), anyString(), any())).thenReturn(3);
        when(history.queryLatest(any())).thenAnswer(call -> {
            LatestRequest request = call.getArgument(0);
            List<Latest> rows = new ArrayList<>();
            for (Point point : request.getPoints()) {
                Latest row = new Latest();
                row.setDatabase(point.getDatabase());
                row.setTable(point.getTable());
                row.setField(point.getField());
                row.setFound(true);
                row.setSourceTime(lagSeconds == 0 ? request.getAtTime() : "2026-10-08 23:58:55");
                row.setValue(point.getField().equals("direction") ? JsonNodeFactory.instance.booleanNode(false)
                        : point.getField().endsWith("coil") ? JsonNodeFactory.instance.textNode(
                                request.getAtTime().contains("23:59") ? coilAtStart : coilAtEnd)
                        : JsonNodeFactory.instance.numberNode(new BigDecimal("2.5")));
                rows.add(row);
            }
            return rows;
        });
    }

    @Test
    void writesPreparedPassesInOrderAndContinuesAfterQualityFailure() {
        when(history.queryStates(any())).thenReturn(Arrays.asList(
                state(1, "2026-10-08 23:59:00", "2026-10-09 00:00:09.123456789"),
                state(2, "2026-10-09 00:00:10", "2026-10-09 00:00:19"),
                state(3, "2026-10-09 00:00:20", "2026-10-09 00:00:29")));
        when(history.queryBounds(any())).thenAnswer(call -> {
            BoundsRequest request = call.getArgument(0);
            Bounds bounds = new Bounds();
            bounds.setFirstTime(request.getStartTime());
            bounds.setLastTime(request.getEndTime());
            return bounds;
        });
        doThrow(new IllegalStateException("质量写入失败")).doNothing().when(quality).write(any());
        List<PassOutputResult> results = useCase.execute("zrm1", start, end);
        assertThat(results).extracting(PassOutputResult::getStatus).containsExactly("FAILED", "SUCCESS");
        assertThat(results.get(0).getReason()).isEqualTo("质量写入失败");
        org.mockito.InOrder calls = inOrder(quality);
        calls.verify(quality).write(argThat(output -> output.getCellCode().equals("ZRM1001")));
        calls.verify(quality).write(argThat(output -> output.getCellCode().equals("ZRM1002")));
    }

    @Test
    void doesNotWriteSkippedPassesOrScanFailures() {
        when(history.queryBounds(any())).thenReturn(new Bounds());
        assertThat(useCase.execute("zrm1", start, end).get(0).getStatus()).isEqualTo("SKIPPED");
        when(history.queryStates(any())).thenThrow(new IllegalStateException("扫描失败"));
        assertThatThrownBy(() -> useCase.execute("zrm1", start, end)).hasMessage("扫描失败");
        verifyNoInteractions(quality);
    }

    @Test
    void sharedDeviceRulesCoverFirstPassLaterPassAndReverseDirection() {
        StatusPointGroup por = group("por1", DevicePosition.RIGHT);
        StatusPointGroup right = group("tr1", DevicePosition.RIGHT);
        StatusPointGroup left = group("tr2", DevicePosition.LEFT);
        assertThat(StatusDeviceResolver.side(por, 1, false, false)).isEqualTo(DeviceSide.UNCOILER);
        assertThat(StatusDeviceResolver.side(right, 1, false, false)).isNull();
        assertThat(StatusDeviceResolver.side(left, 1, false, false)).isEqualTo(DeviceSide.COILER);
        assertThat(StatusDeviceResolver.side(por, 2, false, false)).isNull();
        assertThat(StatusDeviceResolver.side(right, 2, false, false)).isEqualTo(DeviceSide.UNCOILER);
        assertThat(StatusDeviceResolver.side(right, 2, false, true)).isEqualTo(DeviceSide.COILER);
        assertThat(StatusDeviceResolver.side(left, 2, true, false)).isEqualTo(DeviceSide.UNCOILER);
        assertThat(StatusDeviceResolver.side(left, 2, true, true)).isEqualTo(DeviceSide.COILER);
    }

    @Test
    void singleSpeedFrameKeepsNanosecondsAndRejectsInvalidOutputValues() {
        Bounds bounds = new Bounds();
        bounds.setFirstTime("2026-10-09 00:00:09.123456789");
        bounds.setLastTime(bounds.getFirstTime());
        when(history.queryBounds(any())).thenReturn(bounds);
        PassOutputResult result = useCase.prepare("zrm1", start, end).get(0);
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOutput().getStartAt()).isEqualTo(result.getOutput().getEndAt());
        coilAtEnd = "request color 1";
        assertThat(useCase.prepare("zrm1", start, end).get(0).getReason()).contains("钢卷号无效");
    }

    @Test
    void buildsPreviousPassWithClosedBoundsAndOneHourLookback() {
        PassOutputResult result = useCase.prepare("zrm1", start, end).get(0);
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getPassNo()).isEqualTo(1);
        assertThat(result.getOutput().getInMatNo()).isEqualTo("A");
        assertThat(result.getOutput().getCellCode()).isEqualTo("ZRM1001");
        assertThat(result.getOutput().getInMatRepeatProdNo()).isEqualTo(3);
        assertThat(result.getOutput().getEndAt()).isEqualTo(java.time.Instant.parse("2026-10-08T16:00:09.123456789Z"));
        assertThat(result.getOutput().getOutMatLength()).isEqualByComparingTo("2.5");
        assertThat(result.getSourceTimes()).isNotEmpty();
        assertThat(result.getSwitchTime()).isEqualTo(start.plusSeconds(10));
        assertThat(result.getSourceTimes().values()).contains(start.plusSeconds(9).plusNanos(123456789));
        verify(history).queryStates(argThat(r -> r.getStartTime().equals("2026-10-08 23:00:00")));
        verify(history).queryBounds(argThat(r -> r.getStartTime().equals("2026-10-08 23:59:00")
                && r.getEndTime().equals("2026-10-09 00:00:09.123456789") && r.getMin().compareTo(new BigDecimal("0.5")) == 0));
        verify(repeats).findLatestBefore("zrm1", "A", start.minusSeconds(59));
        verifyNoMoreInteractions(repeats);
    }

    /** 首末帧先统一卷号，再使用转换后的卷号查询生产次数并写入质量服务。 */
    @Test
    void convertsThirdLastDigitBeforeComparingCoilsAndWritingQuality() {
        coilAtStart = " \u200bABC3312 ";
        coilAtEnd = "ABC3212";

        PassOutputResult result = useCase.execute("zrm1", start, end).get(0);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getOutput().getInMatNo()).isEqualTo("ABC3212");
        verify(repeats).findLatestBefore("zrm1", "ABC3212", start.minusSeconds(59));
        verify(quality).write(argThat(output -> "ABC3212".equals(output.getInMatNo())));
    }

    /** 覆盖恰好三位、短卷号及其他位置含 3 的边界，避免扩大替换范围。 */
    @ParameterizedTest
    @CsvSource({"312,212", "3,3", "33,33", "ABC213,ABC213", "ABC233,ABC233"})
    void onlyConvertsThirdLastDigit(String pointCoil, String expectedCoil) {
        coilAtStart = pointCoil;
        coilAtEnd = pointCoil;

        PassOutputResult result = useCase.prepare("zrm1", start, end).get(0);

        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getOutput().getInMatNo()).isEqualTo(expectedCoil);
        verify(repeats).findLatestBefore("zrm1", expectedCoil, start.minusSeconds(59));
    }

    @Test
    void skipsMissingSpeedFramesStaleFieldsAndChangedCoilWithoutMovingTimes() {
        when(history.queryBounds(any())).thenReturn(new Bounds());
        assertThat(useCase.prepare("zrm1", start, end).get(0).getReason()).contains("没有速度达标帧");
        Bounds bounds = new Bounds();
        bounds.setFirstTime("2026-10-08 23:59:01");
        bounds.setLastTime("2026-10-09 00:00:09.123456789");
        when(history.queryBounds(any())).thenReturn(bounds);
        lagSeconds = 6;
        assertThat(useCase.prepare("zrm1", start, end).get(0).getReason()).contains("超过 5 秒");
        lagSeconds = 0;
        coilAtEnd = "B";
        assertThat(useCase.prepare("zrm1", start, end).get(0).getReason()).contains("不一致");
        verifyNoInteractions(repeats);
    }

    @Test
    void onlySettlesObservedIncreasesAndKeepsCyclesSeparateAtRequestBoundaries() {
        when(history.queryBounds(any())).thenReturn(new Bounds());
        when(history.queryStates(any())).thenReturn(Arrays.asList(
                state(1, "2026-10-08 23:59:00", "2026-10-08 23:59:59"),
                state(3, "2026-10-09 00:00:00", "2026-10-09 00:00:09"),
                state(0, "2026-10-09 00:00:10", "2026-10-09 00:00:19"),
                state(1, "2026-10-09 00:00:20", "2026-10-09 00:00:29"),
                state(2, "2026-10-09 00:00:30", "2026-10-09 00:00:59"),
                state(3, "2026-10-09 00:01:00", "2026-10-09 00:01:01")));
        assertThat(useCase.prepare("zrm1", start, end)).extracting(PassOutputResult::getPassNo).containsExactly(1, 1);
        State invalid = state(1, "2026-10-09 00:00:20", "2026-10-09 00:00:29");
        invalid.setValue(JsonNodeFactory.instance.numberNode(1.5));
        when(history.queryStates(any())).thenReturn(Arrays.asList(state(1, "2026-10-09 00:00:00", "2026-10-09 00:00:09"),
                invalid, state(3, "2026-10-09 00:00:30", "2026-10-09 00:00:59")));
        assertThat(useCase.prepare("zrm1", start, end)).isEmpty();
    }

    @Test
    void recordsPerPassQueryFailureButPropagatesScanFailure() {
        when(history.queryBounds(any())).thenThrow(new IllegalStateException("查询失败"));
        assertThat(useCase.prepare("zrm1", start, end).get(0).getStatus()).isEqualTo("FAILED");
        when(history.queryStates(any())).thenThrow(new IllegalStateException("扫描失败"));
        assertThatThrownBy(() -> useCase.prepare("zrm1", start, end)).hasMessage("扫描失败");
    }

    private PointConfig config(String name) {
        return PointConfig.builder().name(name).build();
    }

    private StatusPointGroup group(String code, DevicePosition position) {
        return StatusPointGroup.builder().code(code).position(position).coilNo(config(code + "_coil"))
                .remainingLength(config(code + "_length")).build();
    }

    private State state(int pass, String first, String last) {
        State state = new State();
        state.setValue(JsonNodeFactory.instance.numberNode(pass));
        state.setFirstTime(first);
        state.setLastTime(last);
        return state;
    }
}
