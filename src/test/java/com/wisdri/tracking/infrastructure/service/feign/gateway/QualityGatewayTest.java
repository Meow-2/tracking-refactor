package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.service.feign.client.QualityFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityGatewayTest {
    @Test
    void usesInMatNoAsMaterialRequestParameter() throws Exception {
        Method method = QualityFeignClient.class.getMethod(
                "queryNextProductNo", String.class, String.class);

        RequestParam requestParam = method.getParameters()[1].getAnnotation(RequestParam.class);

        assertThat(requestParam.value()).isEqualTo("inMatNo");
    }

    @Test
    void returnsProductNoFromSuccessfulResponse() {
        QualityFeignClient client = mock(QualityFeignClient.class);
        when(client.queryNextProductNo("CP1", "COIL-001"))
                .thenReturn(new R<>(null, "success", 3, true));
        QualityGateway gateway = new QualityGateway();
        ReflectionTestUtils.setField(gateway, "qualityFeignClient", client);

        assertThat(gateway.queryProductNo("CP1", "COIL-001")).isEqualTo(3);
        verify(client).queryNextProductNo("CP1", "COIL-001");
    }

    @Test
    void throwsExceptionWhenResponseIndicatesFailure() {
        QualityFeignClient client = mock(QualityFeignClient.class);
        when(client.queryNextProductNo("CP1", "COIL-001"))
                .thenReturn(new R<>(null, "material not found", null, false));
        QualityGateway gateway = new QualityGateway();
        ReflectionTestUtils.setField(gateway, "qualityFeignClient", client);

        assertThatThrownBy(() -> gateway.queryProductNo("CP1", "COIL-001"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("material not found");
    }
}
