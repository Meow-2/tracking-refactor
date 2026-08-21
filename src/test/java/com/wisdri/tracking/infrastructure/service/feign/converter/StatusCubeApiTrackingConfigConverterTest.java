package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.status.StatusCubeApiTrackingConfigConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusCubeApiTrackingConfigConverterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StatusCubeApiTrackingConfigConverter converter = new StatusCubeApiTrackingConfigConverter();

    @Test
    void convertsValidStatusConfig() throws Exception {
        StatusTrackingConfig config = (StatusTrackingConfig) converter.convert("CP1", node(validConfig()));

        assertThat(config.getTrackingType()).isEqualTo(TrackingType.STATUS);
        assertThat(config.getTracking().getSampleCount()).isEqualTo(3);
        assertThat(config.getTracking().getMinLengthChange()).isEqualByComparingTo("0.5");
        assertThat(config.getTracking().getMonotonicityCheckEnabled()).isFalse();
        assertThat(config.getTracking().getPoints()).hasSize(2);
        assertThat(config.getTracking().getPoints().get(0).getSide()).isEqualTo(DeviceSide.UNCOILER);
        assertThat(config.getTracking().getPoints().get(0).getColorNo().getName())
                .isEqualTo("u1_color");
    }

    @Test
    void convertsEnabledMonotonicityCheck() throws Exception {
        String json = validConfig().replace("\"min_length_change\":0.5",
                "\"min_length_change\":0.5,\"monotonicity_check_enabled\":true");

        StatusTrackingConfig config = (StatusTrackingConfig) converter.convert("CP1", node(json));

        assertThat(config.getTracking().getMonotonicityCheckEnabled()).isTrue();
    }

    @Test
    void rejectsMissingConditionInvalidWindowAndDuplicateDevices() throws Exception {
        assertInvalid(validConfig().replace("\"start_condition\"", "\"missing_condition\""));
        assertInvalid(validConfig().replace("\"sample_count\":3", "\"sample_count\":1"));
        assertInvalid(validConfig().replace("\"code\":\"C1\"", "\"code\":\"U1\""));
        assertInvalid(validConfig().replace("\"remaining_length\":{\"name\":\"c1_length\"}",
                "\"missing_remaining_length\":{\"name\":\"c1_length\"}"));
    }

    private void assertInvalid(String config) throws Exception {
        CubeApiTreeNode node = node(config);
        assertThatThrownBy(() -> converter.convert("CP1", node)).isInstanceOf(TrackingException.class);
    }

    private CubeApiTreeNode node(String config) throws Exception {
        return objectMapper.readValue("{\"data\":{\"default\":" + config + "}}", CubeApiTreeNode.class);
    }

    private String validConfig() {
        return "{"
                + "\"enable\":true,\"mqtt_topic\":\"cp1_status_tracking\","
                + "\"tracking\":{\"point_prefix\":\"/status/\",\"sample_count\":3,"
                + "\"min_length_change\":0.5,"
                + "\"start_condition\":{\"point\":{\"name\":\"run\"},\"threshold\":1},"
                + "\"points\":["
                + "{\"code\":\"U1\",\"name\":\"开卷机1\",\"side\":\"uncoiler\","
                + "\"coil_no\":{\"name\":\"u1_coil\"},"
                + "\"color_no\":{\"name\":\"u1_color\"},"
                + "\"remaining_length\":{\"name\":\"u1_length\"}},"
                + "{\"code\":\"C1\",\"name\":\"卷取机1\",\"side\":\"coiler\","
                + "\"coil_no\":{\"name\":\"c1_coil\"},"
                + "\"remaining_length\":{\"name\":\"c1_length\"}}]}}";
    }
}
