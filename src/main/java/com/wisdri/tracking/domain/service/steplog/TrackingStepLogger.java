package com.wisdri.tracking.domain.service.steplog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将跟踪算法关键步骤输出为单行 JSON，不让日志异常影响业务计算。
 */
@Component
public class TrackingStepLogger {
    private static final Logger STEP_LOG = LoggerFactory.getLogger("tracking.step");
    private static final Logger FALLBACK_LOG = LoggerFactory.getLogger(TrackingStepLogger.class);

    private ObjectMapper objectMapper = JsonUtils.shanghaiTimeDisplayMapper();

    public void log(TrackingInput input, String stage, Map<String, Object> details) {
        log(input, stage, null, details);
    }

    public void log(TrackingInput input,
                    String stage,
                    String segmentCode,
                    Map<String, Object> details) {
        try {
            TrackingStepEvent event = TrackingStepEvent.builder()
                    .timestamp(Instant.now())
                    .unitCode(input == null ? null : input.getUnitCode())
                    .trackingType(input == null ? null : input.getTrackingType())
                    .templateCode(input == null ? null : input.getTemplateCode())
                    .receivedAt(input == null || input.getLatestSnapshot() == null
                            ? null : input.getLatestSnapshot().getReceivedAt())
                    .stage(stage)
                    .segmentCode(segmentCode)
                    .details(details == null ? Collections.emptyMap() : details)
                    .build();
            String trackingType = event.getTrackingType() == null
                    ? "unknown" : event.getTrackingType().getCode();
            MDC.put("trackingType", trackingType);
            try {
                STEP_LOG.info(objectMapper.writeValueAsString(event));
            } finally {
                MDC.remove("trackingType");
            }
        } catch (JsonProcessingException | RuntimeException e) {
            FALLBACK_LOG.warn("输出跟踪算法步骤日志失败，stage={}", stage, e);
        }
    }

    /**
     * 按给定顺序构造步骤详情，参数必须以 key、value 成对出现。
     */
    public static Map<String, Object> details(Object... entries) {
        if (entries == null || entries.length == 0) {
            return Collections.emptyMap();
        }
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("步骤日志详情必须以 key、value 成对出现");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            details.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return details;
    }
}
