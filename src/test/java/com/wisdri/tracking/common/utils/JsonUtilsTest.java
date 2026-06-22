package com.wisdri.tracking.common.utils;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilsTest {
    @Test
    void prettyJsonSupportsJavaTimeInstant() {
        ProcessResult result = ProcessResult.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .generatedAt(Instant.parse("2026-06-23T00:00:00Z"))
                .build();

        String json = JsonUtils.toPrettyJson(Collections.singletonList(result));

        assertThat(json).contains("2026-06-23T00:00:00Z");
    }
}
