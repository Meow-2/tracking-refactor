package com.wisdri.tracking.infrastructure.service.feign.config;

import com.wisdri.tracking.infrastructure.properties.feign.CubeApiProperties;
import com.wisdri.tracking.infrastructure.properties.feign.timeseries.history.TimeSeriesHistoryProperties;
import com.wisdri.tracking.infrastructure.service.feign.config.timeseries.storage.TimeSeriesStorageFeignConfig;
import com.wisdri.tracking.infrastructure.service.feign.config.timeseries.history.TimeSeriesHistoryFeignConfig;
import com.wisdri.tracking.infrastructure.properties.feign.timeseries.storage.TimeSeriesStorageProperties;
import feign.Request;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeignConfigTest {
    @Test
    void historyQueriesUseOneMinuteReadTimeoutWithoutChangingWriteTimeout() {
        TimeSeriesStorageProperties properties = new TimeSeriesStorageProperties();
        properties.setConnectTimeout(1300);
        properties.setReadTimeout(2400);

        TimeSeriesHistoryProperties historyProperties = new TimeSeriesHistoryProperties();
        historyProperties.setConnectTimeout(1700);
        Request.Options history = new TimeSeriesHistoryFeignConfig().timeSeriesHistoryRequestOptions(historyProperties);
        Request.Options write = new TimeSeriesStorageFeignConfig().timeSeriesStorageRequestOptions(properties);

        assertThat(history.connectTimeoutMillis()).isEqualTo(1700);
        assertThat(history.readTimeoutMillis()).isEqualTo(60000);
        assertThat(write.readTimeoutMillis()).isEqualTo(2400);

        historyProperties.setReadTimeout(90000);
        assertThat(new TimeSeriesHistoryFeignConfig().timeSeriesHistoryRequestOptions(historyProperties)
                .readTimeoutMillis()).isEqualTo(90000);
    }

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
