package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.converter.batch.BatchCubeApiTrackingConfigConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class CubeApiBatchTrackingConfigConverterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CubeApiTrackingConfigConverterDispatcher converter;

    @BeforeEach
    void setUp() {
        TrackingProperties properties = new TrackingProperties();
        properties.setUnit("BAF1");
        converter = new CubeApiTrackingConfigConverterDispatcher();
        ReflectionTestUtils.setField(converter, "trackingProperties", properties);
        ReflectionTestUtils.setField(converter, "converters",
                Collections.singletonList(new BatchCubeApiTrackingConfigConverter()));
    }

    @Test
    void convertsSegmentDirectoriesAndRemovesCubeParsingPrefix() throws Exception {
        Map<TrackingType, TrackingConfig> result = converter.convert(tree(validBatchTree()));

        assertThat(result).containsOnlyKeys(TrackingType.BATCH);
        BatchTrackingConfig config = (BatchTrackingConfig) result.get(TrackingType.BATCH);
        assertThat(config.getUnitCode()).isEqualTo("BAF1");
        assertThat(config.getTemplate().resolveCodes()).containsExactly("fb1", "fb2");

        SegmentConfig south = segment(config, "south");
        assertThat(south.getPoints())
                .extracting("name", "type")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("temperature", PointDataType.STRING),
                        org.assertj.core.groups.Tuple.tuple("power", PointDataType.INT)
                );
        assertThat(segment(config, "north").getPoints())
                .extracting("name", "type")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("temperature", PointDataType.FLOAT));

        // 名称看起来像 south 点位，但它位于 common 目录，因此仍归 common。
        assertThat(segment(config, "common").getPoints())
                .extracting("name", "type")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("south_like", PointDataType.BOOL));
    }

    @Test
    void rejectsMissingSegmentDirectory() throws Exception {
        String json = validBatchTree().replace("\"common\": {", "\"missing_common\": {");

        assertThat(converter.convert(tree(json))).isEmpty();
    }

    @Test
    void rejectsPointThatDoesNotMatchConfiguredPrefix() throws Exception {
        String json = validBatchTree().replace("fb2_north_temperature", "unexpected_temperature");

        assertThat(converter.convert(tree(json))).isEmpty();
    }

    @Test
    void rejectsDifferentPointStructureBetweenTemplates() throws Exception {
        String json = validBatchTree().replace(
                "\"fb2_south_power\": {\"itemType\": 2, \"valueType\": \"int\"}",
                "\"fb2_south_other\": {\"itemType\": 2, \"valueType\": \"int\"}"
        );

        assertThat(converter.convert(tree(json))).isEmpty();
    }

    private CubeApiTreeResponse tree(String json) throws Exception {
        return objectMapper.readValue(json, CubeApiTreeResponse.class);
    }

    private SegmentConfig segment(BatchTrackingConfig config, String code) {
        return config.getSegments().stream()
                .filter(segment -> code.equals(segment.getCode()))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private String validBatchTree() {
        return "{\n"
                + "  \"baf1\": {\n"
                + "    \"batch\": {\n"
                + "      \"data\": {\"default\": {\n"
                + "        \"enable\": true,\n"
                + "        \"template\": {\"code\": \"fb{index}\", \"index_from\": 1, \"index_to\": 2},\n"
                + "        \"mqtt_topic\": \"baf1_batch_tracking_{template}\",\n"
                + "        \"segments\": [\n"
                + "          {\"code\": \"south\", \"cube_parsing_prefix\": \"{template}_south_\","
                + " \"point\": [{\"name\": \"temperature\", \"type\": \"string\"}]},\n"
                + "          {\"code\": \"north\", \"cube_parsing_prefix\": \"{template}_north_\"},\n"
                + "          {\"code\": \"common\", \"cube_parsing_prefix\": \"{template}_\"}\n"
                + "        ]\n"
                + "      }},\n"
                + templateDirectory("fb1") + ",\n"
                + templateDirectory("fb2") + "\n"
                + "    }\n"
                + "  }\n"
                + "}";
    }

    private String templateDirectory(String templateCode) {
        return "      \"" + templateCode + "\": {\n"
                + "        \"south\": {\n"
                + "          \"" + templateCode + "_south_temperature\": {\"itemType\": 2, \"valueType\": \"double\"},\n"
                + "          \"" + templateCode + "_south_power\": {\"itemType\": 2, \"valueType\": \"int\"},\n"
                + "          \"nested\": {\"itemType\": 1, \"ignored\": {\"itemType\": 2}}\n"
                + "        },\n"
                + "        \"north\": {\n"
                + "          \"" + templateCode + "_north_temperature\": {\"itemType\": 2, \"valueType\": \"double\"}\n"
                + "        },\n"
                + "        \"common\": {\n"
                + "          \"" + templateCode + "_south_like\": {\"itemType\": 2, \"valueType\": \"boolean\"}\n"
                + "        }\n"
                + "      }";
    }
}
