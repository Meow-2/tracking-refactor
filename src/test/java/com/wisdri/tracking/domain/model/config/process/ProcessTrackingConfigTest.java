package com.wisdri.tracking.domain.model.config.process;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessTrackingConfigTest {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    @Test
    void readsDocumentedProcessConfigJson() throws Exception {
        ProcessTrackingConfig config = objectMapper.readValue(
                new File("doc/config/ZRM1/process.json"),
                ProcessTrackingConfig.class
        );

        TrackingPointGroup entryGroup = config.getTracking().getPoints().get(0);
        TrackingPointGroup deliveryGroup = config.getTracking().getPoints().get(1);
        assertThat(entryGroup.getLength()).containsExactly("entry_right_tr_coil_length");
        assertThat(entryGroup.getCoilNo()).isEqualTo("entry_tr_coil_no");
        assertThat(entryGroup.getIsRollingCoiler()).isFalse();
        assertThat(deliveryGroup.getIsRollingCoiler()).isTrue();
    }
}
