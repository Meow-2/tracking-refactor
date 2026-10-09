package com.wisdri.tracking.infrastructure.properties;

import com.wisdri.tracking.infrastructure.properties.feign.QualityServiceProperties;
import com.wisdri.tracking.infrastructure.service.feign.client.QualityServiceFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用 Spring Boot 启动流程校验 Feign 客户端最终取得的质量服务地址。 */
class QualityServicePropertyResolutionTest {
    @Test
    void productionProfileResolvesKubernetesQualityAddress() {
        StandardEnvironment environment = isolatedEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Collections.singletonMap("QUALITY_BASEURL", "http://quality.example:8080")));

        assertFeignAddress(environment, "http://quality.example:8080");
    }

    @Test
    void productionProfileFallsBackToYamlAddress() {
        assertFeignAddress(isolatedEnvironment(), "http://127.0.0.1:1234");
    }

    /** 排除测试机真实环境变量，确保两种场景只由测试提供的配置决定。 */
    private StandardEnvironment isolatedEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        return environment;
    }

    /** 同时校验类型化配置和生成的 Feign 客户端，确认环境变量覆盖真实生效。 */
    private void assertFeignAddress(StandardEnvironment environment, String expectedAddress) {
        SpringApplication application = new SpringApplication(FeignTestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setEnvironment(environment);
        application.setDefaultProperties(Collections.singletonMap("spring.config.location", "classpath:application-prod.yaml"));
        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getBean(QualityServiceProperties.class).getBaseUrl()).isEqualTo(expectedAddress);
            assertThat(context.getBean(QualityServiceFeignClient.class).toString())
                    .contains("url=" + expectedAddress + ")");
        }
    }

    @SpringBootConfiguration
    @EnableFeignClients(clients = QualityServiceFeignClient.class)
    @EnableConfigurationProperties(QualityServiceProperties.class)
    @ImportAutoConfiguration({FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class})
    static class FeignTestApplication {
    }
}
