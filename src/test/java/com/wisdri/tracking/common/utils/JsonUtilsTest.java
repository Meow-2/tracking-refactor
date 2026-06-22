package com.wisdri.tracking.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilsTest {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    @Test
    void decimalPreservingMapperReadsFloatAsBigDecimal() throws Exception {
        ObjectMapper objectMapper = JsonUtils.decimalPreservingMapper();

        Map<String, Object> values = objectMapper.readValue("{\"speed\":1.25}", MAP_TYPE);

        assertThat(values).containsEntry("speed", new BigDecimal("1.25"));
    }
}
