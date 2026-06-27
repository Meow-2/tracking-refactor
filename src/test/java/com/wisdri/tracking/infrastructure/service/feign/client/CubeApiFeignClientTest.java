package com.wisdri.tracking.infrastructure.service.feign.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

class CubeApiFeignClientTest {

    @Test
    void feignOptionsUseDefaultFeignClientTimeoutProperties() throws NoSuchMethodException {
        Method method = CubeApiFeignClient.CubeApiFeignConfig.class.getMethod(
                "cubeApiRequestOptions",
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
