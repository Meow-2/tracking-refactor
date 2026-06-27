package com.wisdri.tracking.infrastructure.service.feign.client;

import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSeriesStorageFeignClientTest {

    @Test
    void saveColumnUsesConfiguredDatabaseInMappingPath() throws NoSuchMethodException {
        Method method = TimeSeriesStorageFeignClient.class.getMethod(
                "saveColumn",
                String.class,
                TimeSeriesDataRequest.class
        );

        PostMapping postMapping = method.getAnnotation(PostMapping.class);

        assertThat(postMapping.value())
                .containsExactly("/data/db/${time-series-storage.database}/table/{table}/_column");
    }

    @Test
    void saveColumnPathVariableDeclaresTableNameExplicitly() throws NoSuchMethodException {
        Method method = TimeSeriesStorageFeignClient.class.getMethod(
                "saveColumn",
                String.class,
                TimeSeriesDataRequest.class
        );

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);

        assertThat(pathVariable.value()).isEqualTo("table");
    }

    @Test
    void feignOptionsUseDefaultFeignClientTimeoutProperties() throws NoSuchMethodException {
        Method method = TimeSeriesStorageFeignClient.TimeSeriesStorageFeignConfig.class.getMethod(
                "timeSeriesStorageRequestOptions",
                int.class,
                int.class
        );

        assertThat(valueExpression(method.getParameters()[0]))
                .isEqualTo("${feign.client.config.default.connectTimeout:3000}");
        assertThat(valueExpression(method.getParameters()[1]))
                .isEqualTo("${feign.client.config.default.readTimeout:5000}");
    }

    private String valueExpression(Parameter parameter) {
        return parameter.getAnnotation(Value.class).value();
    }
}
