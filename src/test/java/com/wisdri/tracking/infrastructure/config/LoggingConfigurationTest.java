package com.wisdri.tracking.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {
    @Test
    void configuresPersistentRollingLogsAndDedicatedTrackingStream() throws Exception {
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ClassPathResource("logback-spring.xml").getInputStream());
        String xml = StreamUtils.copyToString(
                new ClassPathResource("logback-spring.xml").getInputStream(), StandardCharsets.UTF_8);

        assertTrue(xml.contains("${LOG_DIR}/application.log"));
        assertTrue(xml.contains("${LOG_DIR}/tracking-step.log"));
        assertTrue(xml.contains("${LOG_DIR}/error.log"));
        assertTrue(xml.contains("conversionWord=\"clr\""));
        assertTrue(xml.contains("%clr(%5p)"));
        assertTrue(xml.contains("%d{yyyy-MM-dd HH:mm:ss.SSS, Asia/Shanghai}"));
        assertTrue(xml.contains("%d{yyyy-MM-dd, Asia/Shanghai}"));
        assertTrue(xml.contains("<maxHistory>30</maxHistory>"));
        assertTrue(xml.contains("<totalSizeCap>120MB</totalSizeCap>"));
        assertTrue(xml.contains("<totalSizeCap>150MB</totalSizeCap>"));
        assertTrue(xml.contains("<totalSizeCap>30MB</totalSizeCap>"));
        assertTrue(xml.contains("name=\"tracking.step\" level=\"INFO\" additivity=\"false\""));
        assertTrue(xml.contains("<neverBlock>false</neverBlock>"));
        assertTrue(xml.contains("<discardingThreshold>0</discardingThreshold>"));
    }
}
