package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.converter.process.ProcessCubeApiTrackingConfigConverter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class CubeApiTrackingConfigConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertReadsDynamicTreeIntoProcessConfig() throws Exception {
        CubeApiTreeResponse tree = objectMapper.readValue("{\n"
                + "  \"itemType\": 1,\n"
                + "  \"data\": {\"name\": \"root metadata\"},\n"
                + "  \"other\": {\"process\": {\"data\": {\"default\": {\"enable\": false}}}},\n"
                + "  \"cp1\": {\n"
                + "    \"process\": {\n"
                + "      \"data\": {\"default\": {\"enable\": true, \"mqtt_topic\": \"/cp1/process\"}},\n"
                + "      \"tech\": {\n"
                + "        \"sf\": {\n"
                + "          \"data\": {\"default\": {\"code\": \"sf\", \"name\": \"均热段\","
                + " \"point_prefix\": \"/aygg_tracking/cp1/process/tech/sf/\"}},\n"
                + "          \"temperature\": {\"itemType\": 2, \"valueType\": \"double\"},\n"
                + "          \"enabled\": {\"itemType\": 2, \"data\": {\"valueType\": \"boolean\"}},\n"
                + "          \"directory\": {\"itemType\": 1}\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}", CubeApiTreeResponse.class);

        TrackingProperties properties = new TrackingProperties();
        properties.setUnit("CP1");
        CubeApiTrackingConfigConverterDispatcher converter = new CubeApiTrackingConfigConverterDispatcher();
        ReflectionTestUtils.setField(converter, "trackingProperties", properties);
        ReflectionTestUtils.setField(converter, "converters",
                Collections.singletonList(new ProcessCubeApiTrackingConfigConverter()));

        Map<TrackingType, TrackingConfig> result = converter.convert(tree);

        assertThat(result).containsOnlyKeys(TrackingType.PROCESS);
        ProcessTrackingConfig config = (ProcessTrackingConfig) result.get(TrackingType.PROCESS);
        assertThat(config.getUnitCode()).isEqualTo("CP1");
        assertThat(config.getEnable()).isTrue();
        assertThat(config.getMqttTopic()).isEqualTo("/cp1/process");
        assertThat(config.getSegments()).hasSize(1);
        assertThat(config.getSegments().get(0).getCode()).isEqualTo("sf");
        assertThat(config.getSegments().get(0).getPointPrefix())
                .isEqualTo("/aygg_tracking/cp1/process/tech/sf/");
        assertThat(config.getSegments().get(0).getPoints())
                .extracting("name", "type")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("temperature", PointDataType.FLOAT),
                        org.assertj.core.groups.Tuple.tuple("enabled", PointDataType.BOOL)
                );
    }
}
