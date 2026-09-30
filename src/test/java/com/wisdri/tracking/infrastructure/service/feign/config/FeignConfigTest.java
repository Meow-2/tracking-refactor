package com.wisdri.tracking.infrastructure.service.feign.config;

import com.wisdri.tracking.infrastructure.properties.feign.CubeApiProperties;
import com.wisdri.tracking.infrastructure.properties.feign.TimeSeriesStorageProperties;
import feign.Request;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeignConfigTest {
    @Test
    void createsCubeApiOptionsFromTypedProperties() {
        CubeApiProperties properties = new CubeApiProperties();
        properties.setConnectTimeout(1100);
        properties.setReadTimeout(2200);

        Request.Options options = new CubeApiFeignConfig().cubeApiRequestOptions(properties);

        assertThat(options.connectTimeoutMillis()).isEqualTo(1100);
        assertThat(options.readTimeoutMillis()).isEqualTo(2200);
    }

    @Test
    void createsTimeSeriesOptionsFromMovedProperties() {
        TimeSeriesStorageProperties properties = new TimeSeriesStorageProperties();
        properties.setConnectTimeout(1300);
        properties.setReadTimeout(2400);

        Request.Options options = new TimeSeriesStorageFeignConfig()
                .timeSeriesStorageRequestOptions(properties);

        assertThat(options.connectTimeoutMillis()).isEqualTo(1300);
        assertThat(options.readTimeoutMillis()).isEqualTo(2400);
    }
}
