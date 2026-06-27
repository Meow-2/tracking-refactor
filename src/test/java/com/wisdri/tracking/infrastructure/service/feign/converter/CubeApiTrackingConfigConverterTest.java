package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CubeApiTrackingConfigConverterTest {

    @Test
    void processSegmentPointsAreConvertedToTypedPointObjectsUsingCubeValueType() throws Exception {
        JsonNode tree = JsonUtils.decimalPreservingMapper().readTree("{\n" +
                "  \"cp1\": {\n" +
                "    \"process\": {\n" +
                "      \"data\": { \"default\": { \"enable\": true } },\n" +
                "      \"tech\": {\n" +
                "        \"NOF\": {\n" +
                "          \"id\": 140608,\n" +
                "          \"code\": \"metadata_nof\",\n" +
                "          \"name\": \"NOF段\",\n" +
                "          \"itemType\": 1,\n" +
                "          \"path\": \"/aygg_tracking/cp1/process/tech/nof\",\n" +
                "          \"valueType\": null,\n" +
                "          \"unit\": null,\n" +
                "          \"tag\": null,\n" +
                "          \"data\": { \"default\": { \"code\": \"nof\", \"name\": \"NOF段\" } },\n" +
                "          \"nof_temp\": { \"valueType\": \"Float\" },\n" +
                "          \"nof_running\": { \"data\": { \"valueType\": \"Boolean\" } },\n" +
                "          \"nof_pass_no\": { \"valueType\": \"Int32\" },\n" +
                "          \"nof_remark\": { \"valueType\": \"String\" }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"FCL1\": {\n" +
                "    \"process\": {\n" +
                "      \"data\": { \"default\": { \"enable\": true } },\n" +
                "      \"tech\": {}\n" +
                "    }\n" +
                "  }\n" +
                "}");
        TrackingProperties trackingProperties = new TrackingProperties();
        trackingProperties.setUnit("cp1");
        CubeApiTrackingConfigConverter converter = new CubeApiTrackingConfigConverter();
        ReflectionTestUtils.setField(converter, "trackingProperties", trackingProperties);

        Map<TrackingType, TrackingConfig> configs = converter.convert(tree);

        assertThat(configs).containsOnlyKeys(TrackingType.PROCESS);
        ProcessTrackingConfig config = (ProcessTrackingConfig) configs.get(TrackingType.PROCESS);
        assertThat(config.getUnitCode()).isEqualTo("cp1");
        assertThat(config.getTrackingType()).isEqualTo(TrackingType.PROCESS);
        assertThat(config.getSegments()).hasSize(1);
        assertThat(config.getSegments().get(0).getCode()).isEqualTo("nof");
        assertThat(config.getSegments().get(0).getName()).isEqualTo("NOF段");
        assertThat(config.getSegments().get(0).getPoints())
                .extracting("name", "type")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("nof_temp", com.wisdri.tracking.domain.model.config.process.PointDataType.FLOAT),
                        org.assertj.core.groups.Tuple.tuple("nof_running", com.wisdri.tracking.domain.model.config.process.PointDataType.BOOL),
                        org.assertj.core.groups.Tuple.tuple("nof_pass_no", com.wisdri.tracking.domain.model.config.process.PointDataType.INT),
                        org.assertj.core.groups.Tuple.tuple("nof_remark", com.wisdri.tracking.domain.model.config.process.PointDataType.STRING)
                );
    }
}
