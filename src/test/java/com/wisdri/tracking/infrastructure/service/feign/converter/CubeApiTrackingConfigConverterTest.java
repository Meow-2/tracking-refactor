package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.converter.process.ProcessCubeApiTrackingConfigConverter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                + "          \"temperature\": {\"itemType\": 2, \"valueType\": \"float\"},\n"
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
                        org.assertj.core.groups.Tuple.tuple("enabled", PointDataType.BOOLEAN)
                );
    }

    @Test
    void convertsFixedAndPointCellCodeAndPreservesThemThroughConfigJson() throws Exception {
        ProcessCubeApiTrackingConfigConverter converter = new ProcessCubeApiTrackingConfigConverter();
        ObjectMapper cacheMapper = JsonUtils.shanghaiTimeDisplayMapperBuilder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                .build();

        ProcessTrackingConfig fixed = (ProcessTrackingConfig) converter.convert("CP1", processNode("2"));
        assertThat(fixed.getSegments().get(0).getCellCodeValue()).isEqualTo(2);
        assertThat(fixed.getSegments().get(0).getCellCodePoint()).isNull();
        ProcessTrackingConfig fixedRestored = cacheMapper.readValue(
                cacheMapper.writeValueAsString(fixed), ProcessTrackingConfig.class);
        assertThat(fixedRestored.getSegments().get(0).getCellCodeValue()).isEqualTo(2);

        ProcessTrackingConfig point = (ProcessTrackingConfig) converter.convert("CP1",
                processNode("{\"name\":\"/aygg_tracking/cp1/process/tracking/pass_no_pv\",\"type\":\"short\"}"));
        assertThat(point.getSegments().get(0).getCellCodePoint().getName())
                .isEqualTo("/aygg_tracking/cp1/process/tracking/pass_no_pv");
        ProcessTrackingConfig pointRestored = cacheMapper.readValue(
                cacheMapper.writeValueAsString(point), ProcessTrackingConfig.class);
        assertThat(pointRestored.getSegments().get(0).getCellCodePoint().getType())
                .isEqualTo(PointDataType.SHORT);
    }

    @Test
    void rejectsInvalidFixedCellCode() throws Exception {
        ProcessCubeApiTrackingConfigConverter converter = new ProcessCubeApiTrackingConfigConverter();
        assertThatThrownBy(() -> converter.convert("CP1", processNode("1000")))
                .isInstanceOf(TrackingException.class)
                .hasMessageContaining("segment.cell_code");
    }

    /** 只构造本测试关心的 Cube 默认配置及一个工艺段。 */
    private CubeApiTreeNode processNode(String cellCodeJson) throws Exception {
        return objectMapper.readValue("{\"data\":{\"default\":{\"enable\":true}},"
                + "\"tech\":{\"sf\":{\"data\":{\"default\":{\"code\":\"sf\","
                + "\"point_prefix\":\"/aygg_tracking/cp1/process/tech/sf/\","
                + "\"cell_code\":" + cellCodeJson + "}}}}}", CubeApiTreeNode.class);
    }
}
