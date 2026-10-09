package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.domain.model.tracking.status.RollingPassOutput;
import com.wisdri.tracking.infrastructure.dto.feign.quality.CellBloodOutputRequest;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.infrastructure.service.feign.client.QualityServiceFeignClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityServiceGatewayTest {
    @Test
    void convertsOutputAndAcceptsOnlyCode200() {
        QualityServiceFeignClient client = mock(QualityServiceFeignClient.class);
        QualityServiceGateway gateway = gateway(client);
        R<Void> success = response(200);
        when(client.writeCellBloodOutput(any(CellBloodOutputRequest.class))).thenReturn(success);

        gateway.write(output());

        ArgumentCaptor<CellBloodOutputRequest> captor = ArgumentCaptor.forClass(CellBloodOutputRequest.class);
        verify(client).writeCellBloodOutput(captor.capture());
        CellBloodOutputRequest request = captor.getValue();
        assertThat(request.getUnitCode()).isEqualTo("ZRM1");
        assertThat(request.getInMatRepeatProdNo()).isEqualTo("2");
        assertThat(request.getCellCode()).isEqualTo("ZRM1003");
        assertThat(request.getStartProdTime()).isEqualTo("2026-10-09 08:00:00");
        assertThat(request.getEndProdTime()).isEqualTo("2026-10-09 08:01:00");
        assertThat(request.getOutMatThick()).isEqualByComparingTo("0.28");
        assertThat(request.getOutMatLength()).isEqualByComparingTo("125.5");
    }

    @Test
    void retriesNon200ResponseThenReportsFinalFailure() {
        QualityServiceFeignClient client = mock(QualityServiceFeignClient.class);
        when(client.writeCellBloodOutput(any(CellBloodOutputRequest.class))).thenReturn(response(500));

        assertThatThrownBy(() -> gateway(client).write(output())).isInstanceOf(RuntimeException.class);

        verify(client, times(3)).writeCellBloodOutput(any(CellBloodOutputRequest.class));
    }

    @Test
    void retriesTransportFailureAndThenSucceeds() {
        QualityServiceFeignClient client = mock(QualityServiceFeignClient.class);
        when(client.writeCellBloodOutput(any(CellBloodOutputRequest.class)))
                .thenThrow(new IllegalStateException("timeout")).thenReturn(response(200));

        gateway(client).write(output());

        verify(client, times(2)).writeCellBloodOutput(any(CellBloodOutputRequest.class));
    }

    private QualityServiceGateway gateway(QualityServiceFeignClient client) {
        QualityServiceGateway gateway = new QualityServiceGateway();
        ReflectionTestUtils.setField(gateway, "client", client);
        ReflectionTestUtils.setField(gateway, "retryExecutor", new ExternalServiceRetryExecutor());
        return gateway;
    }

    @Test
    void readsNumericResponseCodeAsCommonResponse() throws Exception {
        R<?> response = JsonUtils.decimalPreservingMapper().readValue(
                "{\"code\":200,\"message\":\"ok\"}", R.class);
        assertThat(response.getCode()).isEqualTo("200");
    }

    private R<Void> response(int code) {
        return new R<>(String.valueOf(code), "result", null, null);
    }

    private RollingPassOutput output() {
        return RollingPassOutput.builder().unitCode("ZRM1").inMatNo("COIL-A")
                .inMatRepeatProdNo(2).cellCode("ZRM1003")
                .startAt(Instant.parse("2026-10-09T00:00:00Z"))
                .endAt(Instant.parse("2026-10-09T00:01:00Z"))
                .outMatThick(new BigDecimal("0.28"))
                .outMatLength(new BigDecimal("125.5")).build();
    }
}
