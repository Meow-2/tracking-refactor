package com.wisdri.tracking.domain.model.config.process;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.wisdri.tracking.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessTrackingConfigTypedPointTest {

    private final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapperBuilder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    @Test
    void readsTypedPointConfigFromRedisJsonShape() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("doc/config/CP1/process.json")), StandardCharsets.UTF_8);

        ProcessTrackingConfig config = objectMapper.readValue(json, ProcessTrackingConfig.class);

        assertThat(config.getTracking().getSpeedPoint().getName()).isEqualTo("center_speed_pv");
        assertThat(config.getTracking().getSpeedPoint().getType()).isEqualTo(PointDataType.FLOAT);
        assertThat(config.getTracking().getStartCondition().getPoint().getType()).isEqualTo(PointDataType.FLOAT);
        assertThat(config.getTracking().getPoints().get(0).getLength().get(0).getType()).isEqualTo(PointDataType.FLOAT);
        assertThat(config.getTracking().getPoints().get(0).getCoilNo().getType()).isEqualTo(PointDataType.STRING);
        assertThat(config.getSegments().get(0).getCode()).isEqualTo("nof");
        assertThat(config.getSegments().get(0).getName()).isEqualTo("NOF段");
        assertThat(config.getSegments().get(0).getPoints().get(0).getName()).isEqualTo("nof1_furnace_temp_pv");
        assertThat(config.getSegments().get(0).getPoints().get(0).getType()).isEqualTo(PointDataType.FLOAT);
    }
}
