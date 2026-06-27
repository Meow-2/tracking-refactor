package com.wisdri.tracking.infrastructure.dto.feign.cube;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CubeApiTreeRequestTest {

    @Test
    void defaultRequestUsesParaRangeThreeToFetchConfigAndPointValueType() {
        CubeApiTreeRequest request = CubeApiTreeRequest.defaultRequest("/aygg_tracking");

        assertThat(request.getLevel()).isNull();
        assertThat(request.getParaRange()).isEqualTo(3);
        assertThat(request.getPath()).isEqualTo("/aygg_tracking");
        assertThat(request.getPathHeader()).isFalse();
        assertThat(request.getOnlyDir()).isFalse();
    }
}
