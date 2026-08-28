package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingLengthMode;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingConfig;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.trimming.TrimmingCubeApiTrackingConfigConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class TrimmingCubeApiTrackingConfigConverterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsProcessCompatibleStructureAndStatusLengthMode() throws Exception {
        CubeApiTreeNode node = objectMapper.readValue("{\n"
                + "  \"data\": {\"default\": {\"enable\": true, \"mqtt_topic\": \"cbl1_trimming_tracking\","
                + " \"tracking\": {\"length_mode\": \"status\"}}},\n"
                + "  \"tech\": {\"disc\": {\n"
                + "    \"data\": {\"default\": {\"code\": \"disc\", \"name\": \"圆盘剪\","
                + " \"point_prefix\": \"/trimming/tech/\"}},\n"
                + "    \"width_pv\": {\"itemType\": 2, \"valueType\": \"float\"},\n"
                + "    \"width_sv\": {\"itemType\": 2, \"valueType\": \"float\"}\n"
                + "  }}\n"
                + "}", CubeApiTreeNode.class);

        TrimmingTrackingConfig config = (TrimmingTrackingConfig)
                new TrimmingCubeApiTrackingConfigConverter().convert("CBL1", node);

        assertThat(config.getTracking().getLengthMode()).isEqualTo(TrimmingLengthMode.STATUS);
        assertThat(config.getSegments()).singleElement().satisfies(segment -> {
            assertThat(segment.getCode()).isEqualTo("disc");
            assertThat(segment.getPoints()).extracting("name", "type")
                    .containsExactly(tuple("width_pv", PointDataType.FLOAT),
                            tuple("width_sv", PointDataType.FLOAT));
        });
    }
}
