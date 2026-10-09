package com.wisdri.tracking.infrastructure.properties;

import com.wisdri.tracking.infrastructure.service.feign.client.QualityServiceFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/** 校验 Feign 地址在 Kubernetes 环境变量和 YAML 默认值下的实际解析结果。 */
class QualityServicePropertyResolutionTest {
    @Test
    void productionProfileResolvesKubernetesQualityAddress() throws Exception {
        StandardEnvironment environment = productionEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("kubernetes",
                Collections.singletonMap("QUALITY_BASEURL", "http://quality.example:8080")));

        assertThat(resolveFeignAddress(environment))
                .isEqualTo("http://quality.example:8080");
    }

    @Test
    void productionProfileFallsBackToYamlAddress() throws Exception {
        assertThat(resolveFeignAddress(productionEnvironment()))
                .isEqualTo("http://127.0.0.1:1234");
    }

    private StandardEnvironment productionEnvironment() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new YamlPropertySourceLoader().load("production",
                new FileSystemResource("src/main/resources/application-prod.yaml")).get(0));
        return environment;
    }

    /** 使用 Feign 注解中的真实占位符验证解析，避免测试表达式与生产配置漂移。 */
    private String resolveFeignAddress(StandardEnvironment environment) {
        String placeholder = QualityServiceFeignClient.class.getAnnotation(FeignClient.class).url();
        return environment.resolveRequiredPlaceholders(placeholder);
    }
}
