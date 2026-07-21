package com.wisdri.tracking.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KubernetesLoggingConfigTest {
    @Test
    void mountsEachTrackingPvcAndConfiguresLogPath() throws Exception {
        for (String unit : Arrays.asList("baf1", "cbl1", "cp1", "csl1", "dcl1", "fcl1", "zrm1")) {
            Path directory = Paths.get("deploy", "k8s", unit);
            String deployment = new String(Files.readAllBytes(
                    directory.resolve("tracking-" + unit + "-app.yaml")), StandardCharsets.UTF_8);
            String configMap = new String(Files.readAllBytes(
                    directory.resolve("tracking-" + unit + "-cm.yaml")), StandardCharsets.UTF_8);

            new Yaml().loadAll(deployment).forEach(document -> assertTrue(document instanceof Map, unit));
            assertTrue(new Yaml().load(configMap) instanceof Map, unit);
            assertTrue(deployment.contains("mountPath: /logs"), unit);
            assertTrue(deployment.contains("claimName: tracking-" + unit + "-pvc"), unit);
            assertTrue(configMap.contains("LOGGING_FILE_PATH: \"/logs\""), unit);
        }
    }
}
