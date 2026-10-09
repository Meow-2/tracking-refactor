package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.coiler.CoilerResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CoilerTrackingAlgorithmImplTest {
    private TrackingStepLogger trackingStepLogger;
    private CoilerTrackingAlgorithmImpl algorithm;

    @BeforeEach
    void setUp() {
        trackingStepLogger = mock(TrackingStepLogger.class);
        algorithm = new CoilerTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", trackingStepLogger);
    }

    @Test
    void convertsStatusResultsToCoilerResults() {
        Instant detectedAt = Instant.parse("2026-08-27T01:00:00Z");
        StatusResult uncoiler = StatusResult.builder()
                .unitCode("CP1").trackingType(TrackingType.STATUS)
                .side(DeviceSide.UNCOILER)
                .coilNo("COIL-1").repeatProdNo(2).passNo(3).cellCode("CP1003")
                .coilerMethod("11").coilerMethodName("上开卷")
                .deviceCode("por1").deviceName("1#开卷机")
                .maxLength(new BigDecimal("1200.50")).receivedAt(detectedAt).build();
        StatusResult coiler = StatusResult.builder()
                .unitCode("CP1").trackingType(TrackingType.STATUS)
                .side(DeviceSide.COILER)
                .coilNo("COIL-2").repeatProdNo(3).cellCode("CP1001")
                .coilerMethod("19").coilerMethodName("上卷取")
                .deviceCode("tr1").deviceName("1#卷取机")
                .maxLength(new BigDecimal("10")).receivedAt(detectedAt).build();

        List<CoilerResult> results = algorithm.calculate(TrackingInput.builder()
                .unitCode("CP1").trackingType(TrackingType.COILER)
                .statusContext(StatusTrackingContext.builder()
                        .results(Arrays.asList(uncoiler, coiler)).build())
                .build());

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).satisfies(result -> {
            assertThat(result.getTrackingType()).isEqualTo(TrackingType.COILER);
            assertThat(result.getInMatNo()).isEqualTo("COIL-1");
            assertThat(result.getRepeatProdNo()).isEqualTo(2);
            assertThat(result.getPassNo()).isEqualTo(3);
            assertThat(result.getCellCode()).isEqualTo("CP1003");
            assertThat(result.getUncoilerMethod()).isEqualTo("11");
            assertThat(result.getUncoilerMethodName()).isEqualTo("上开卷");
            assertThat(result.getCoilerMethod()).isNull();
            assertThat(result.getCoilerMethodName()).isNull();
            assertThat(result.getUncoilerDeviceCode()).isEqualTo("por1");
            assertThat(result.getUncoilerDeviceName()).isEqualTo("1#开卷机");
            assertThat(result.getUncoilerMaxLength()).isEqualByComparingTo("1200.50");
            assertThat(result.getCoilerDeviceCode()).isNull();
            assertThat(result.getCoilerMaxLength()).isNull();
            assertThat(result.getReceivedAt()).isEqualTo(detectedAt);
        });
        assertThat(results.get(1).getInMatNo()).isEqualTo("COIL-2");
        assertThat(results.get(1).getCellCode()).isEqualTo("CP1001");
        assertThat(results.get(1).getCoilerMethod()).isEqualTo("19");
        assertThat(results.get(1).getCoilerMethodName()).isEqualTo("上卷取");
        assertThat(results.get(1).getUncoilerMethod()).isNull();
        assertThat(results.get(1).getUncoilerMethodName()).isNull();
        assertThat(results.get(1).getCoilerDeviceCode()).isEqualTo("tr1");
        assertThat(results.get(1).getCoilerDeviceName()).isEqualTo("1#卷取机");
        assertThat(results.get(1).getCoilerMaxLength()).isEqualByComparingTo("10");
        assertThat(results.get(1).getUncoilerDeviceCode()).isNull();
        assertThat(results.get(1).getUncoilerMaxLength()).isNull();
        verify(trackingStepLogger).log(any(TrackingInput.class), eq("计算开始"), anyMap());
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("开卷卷取结果生成"), eq("por1"), anyMap());
        verify(trackingStepLogger).log(any(TrackingInput.class),
                eq("开卷卷取结果生成"), eq("tr1"), anyMap());
        verify(trackingStepLogger).log(any(TrackingInput.class), eq("计算完成"), anyMap());
    }

    @Test
    void rejectsMissingStatusResults() {
        assertThatThrownBy(() -> algorithm.calculate(TrackingInput.builder()
                .trackingType(TrackingType.COILER).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsStatusResultWithMissingBusinessFields() {
        assertThatThrownBy(() -> algorithm.calculate(TrackingInput.builder()
                .trackingType(TrackingType.COILER)
                .statusContext(StatusTrackingContext.builder()
                        .results(Collections.singletonList(StatusResult.builder()
                                .unitCode("CP1")
                                .coilNo("COIL-1")
                                .build()))
                        .build())
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("开卷卷取状态结果业务字段不能为空");
    }
}
