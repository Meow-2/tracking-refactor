package com.wisdri.tracking.infrastructure.service.feign.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualityGatewayTest {
    @Test
    void temporarilyReturnsOneAsProductNo() {
        QualityGateway gateway = new QualityGateway();

        assertThat(gateway.queryProductNo("COIL-001")).isEqualTo(1);
    }
}
