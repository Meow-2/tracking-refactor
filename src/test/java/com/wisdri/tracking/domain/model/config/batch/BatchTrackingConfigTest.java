package com.wisdri.tracking.domain.model.config.batch;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchTrackingConfigTest {
    private final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapperBuilder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    @Test
    void readsCurrentBatchJsonAndExpandsTemplateCodes() throws Exception {
        byte[] json = Files.readAllBytes(Paths.get("docs/config/BAF1/batch.json"));

        BatchTrackingConfig config = objectMapper.readValue(json, BatchTrackingConfig.class);
        config.setUnitCode("BAF1");
        config.setTrackingType(TrackingType.BATCH);

        assertThat(config.getEnable()).isTrue();
        assertThat(config.getTrackingType()).isEqualTo(TrackingType.BATCH);
        assertThat(config.getMqttTopic()).isEqualTo("baf1_batch_tracking_{template}");
        assertThat(config.getTracking().getPoints())
                .extracting(TrackingPointGroup::getSegment)
                .containsExactly("north", "south");
        assertThat(config.getTracking().getStartCondition().getPoint().getType())
                .isEqualTo(PointDataType.SHORT);
        assertThat(config.getTracking().getCurrentClearThreshold()).isEqualTo(1);
        assertThat(config.getSegments())
                .extracting(SegmentConfig::getCode)
                .containsExactly("south", "north", "common");
        List<String> templateCodes = config.getTemplate().resolveCodes();
        assertThat(templateCodes).hasSize(36);
        assertThat(templateCodes.get(0)).isEqualTo("fb1");
        assertThat(templateCodes.get(35)).isEqualTo("fb36");
    }

    @Test
    void mapsOptionalSingularPointFieldToJavaPointsList() throws Exception {
        BatchTrackingConfig config = objectMapper.readValue(
                "{\"segments\":[{\"code\":\"south\",\"point\":["
                        + "{\"name\":\"fb_temp_sv\",\"type\":\"float\"}]}]}",
                BatchTrackingConfig.class
        );

        assertThat(config.getSegments().get(0).getPoints())
                .extracting("name", "type")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("fb_temp_sv", PointDataType.FLOAT));
    }

    @Test
    void rejectsInvalidTemplateRange() {
        TemplateConfig template = TemplateConfig.builder()
                .code("fb{index}")
                .indexFrom(2)
                .indexTo(1)
                .build();

        assertThatThrownBy(template::resolveCodes)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("起点");
    }

    @Test
    void rejectsTemplateCodeWithoutIndexPlaceholder() {
        TemplateConfig template = TemplateConfig.builder()
                .code("fb")
                .indexFrom(1)
                .indexTo(36)
                .build();

        assertThatThrownBy(template::resolveCodes)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{index}");
    }
}
