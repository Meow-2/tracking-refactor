package com.wisdri.tracking.common.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilsTest {
    @Test
    void prettyJsonDisplaysInstantInAsiaShanghai() {
        String json = JsonUtils.toPrettyJson(Collections.singletonMap(
                "receivedAt",
                Instant.parse("2026-06-27T00:29:40.323Z")
        ));

        assertThat(json).contains("\"receivedAt\" : \"2026-06-27T08:29:40.323+08:00\"");
    }
}
