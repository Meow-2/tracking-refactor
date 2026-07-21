package com.wisdri.tracking.domain.service.tracking.trace;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingStepLoggerTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger("tracking.step");
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void writesSingleLineJsonWithCorrelationFields() throws Exception {
        TrackingStepLogger stepLogger = new TrackingStepLogger();
        Instant receivedAt = Instant.parse("2026-07-18T08:00:00Z");
        TrackingInput input = TrackingInput.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .templateCode("fb1")
                .latestSnapshot(PointSnapshot.builder().receivedAt(receivedAt).build())
                .build();

        stepLogger.log(input, "生产条件检查",
                TrackingStepLogger.details("actual", 1, "passed", true));

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertFalse(message.contains("\n"));
        JsonNode json = JsonUtils.decimalPreservingMapper().readTree(message);
        assertEquals("BAF1", json.path("unitCode").asText());
        assertEquals("BATCH", json.path("trackingType").asText());
        assertEquals("fb1", json.path("templateCode").asText());
        assertEquals("2026-07-18T16:00:00+08:00", json.path("receivedAt").asText());
        assertEquals("+08:00", json.path("timestamp").asText()
                .substring(json.path("timestamp").asText().length() - 6));
        assertEquals("生产条件检查", json.path("stage").asText());
        assertEquals(1, json.path("details").path("actual").asInt());
    }

    @Test
    void serializationFailureDoesNotInterruptTracking() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") { });
        TrackingStepLogger stepLogger = new TrackingStepLogger();
        ReflectionTestUtils.setField(stepLogger, "objectMapper", objectMapper);

        assertDoesNotThrow(() -> stepLogger.log(null, "计算开始",
                TrackingStepLogger.details("configPresent", true)));
    }
}
