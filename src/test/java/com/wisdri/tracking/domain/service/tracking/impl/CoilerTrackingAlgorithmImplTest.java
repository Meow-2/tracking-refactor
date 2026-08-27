package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.coiler.CoilerResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoilerTrackingAlgorithmImplTest {
    private final CoilerTrackingAlgorithmImpl algorithm = new CoilerTrackingAlgorithmImpl();

    @Test
    void convertsStatusResultsToCoilerResults() {
        Instant detectedAt = Instant.parse("2026-08-27T01:00:00Z");
        StatusResult uncoiler = StatusResult.builder()
                .unitCode("CP1").trackingType(TrackingType.STATUS)
                .coilNo("COIL-1").productNo(2)
                .coilerMethod("11").coilerMethodName("上开卷")
                .deviceCode("por1").deviceName("1#开卷机")
                .maxLength(new BigDecimal("1200.50")).receivedAt(detectedAt).build();
        StatusResult coiler = StatusResult.builder()
                .unitCode("CP1").trackingType(TrackingType.STATUS)
                .coilNo("COIL-2").productNo(3)
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
            assertThat(result.getInMatNoProdNo()).isEqualTo(2);
            assertThat(result.getCoilerMethod()).isEqualTo("11");
            assertThat(result.getDeviceCode()).isEqualTo("por1");
            assertThat(result.getMaxLength()).isEqualByComparingTo("1200.50");
            assertThat(result.getReceivedAt()).isEqualTo(detectedAt);
        });
        assertThat(results.get(1).getInMatNo()).isEqualTo("COIL-2");
        assertThat(results.get(1).getDeviceCode()).isEqualTo("tr1");
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
