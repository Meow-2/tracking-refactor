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
        assertThat(config.getTracking().getCurrentClearThreshold()).isEqualTo(1);
        assertThat(config.getTracking().getMonotonicityCheckEnabled()).isFalse();
        assertThat(config.getTracking().getPoints()).hasSize(2);
        assertThat(config.getTracking().getPoints().get(0).getSide()).isEqualTo(DeviceSide.UNCOILER);
        assertThat(config.getTracking().getPoints().get(0).getColorNo().getName())
                .isEqualTo("u1_color");
        assertThat(config.getTracking().getCoilerMethodDef().getUncoiler().getCode())
                .containsExactly("11", "91");
        assertThat(config.getTracking().getPoints().get(0).getCoilerMethod().getDefaultValue()).isTrue();
        assertThat(config.getTracking().getPoints().get(0).getCoilerMethod().getFalseIndex()).isZero();
        assertThat(config.getTracking().getPoints().get(0).getCoilerMethod().getType().getCode())
                .isEqualTo("boolean");
    }

    @Test
    void convertsEnabledMonotonicityCheck() throws Exception {
        String json = validConfig().replace("\"min_length_change\":0.5",
                "\"min_length_change\":0.5,\"monotonicity_check_enabled\":true");

        StatusTrackingConfig config = (StatusTrackingConfig) converter.convert("CP1", node(json));

        assertThat(config.getTracking().getMonotonicityCheckEnabled()).isTrue();
    }

    @Test
    void convertsConfiguredCurrentClearThresholdAndRejectsInvalidValues() throws Exception {
        String configured = validConfig().replace("\"min_length_change\":0.5",
                "\"min_length_change\":0.5,\"current_clear_threshold\":3");

        StatusTrackingConfig config = (StatusTrackingConfig) converter.convert("CP1", node(configured));

        assertThat(config.getTracking().getCurrentClearThreshold()).isEqualTo(3);
        assertInvalid(configured.replace("\"current_clear_threshold\":3", "\"current_clear_threshold\":0"));
        assertInvalid(configured.replace("\"current_clear_threshold\":3", "\"current_clear_threshold\":-1"));
        assertInvalid(configured.replace("\"current_clear_threshold\":3", "\"current_clear_threshold\":null"));
    }

    @Test
    void convertsAndValidatesRollingStatusConfig() throws Exception {
        String rolling = "\"rolling\":{"
                + "\"direct_point\":{\"name\":\"rolling_direction\",\"type\":\"boolean\"},"
                + "\"pass_no_point\":{\"name\":\"pass_no_pv\",\"type\":\"short\"},"
                + "\"direct_reverse\":false},";
        String json = validConfig().replace("\"coiler_method_def\":", rolling + "\"coiler_method_def\":");

        StatusTrackingConfig config = (StatusTrackingConfig) converter.convert("ZRM1", node(json));

        assertThat(config.getTracking().getRolling().getDirectPoint().getName())
                .isEqualTo("rolling_direction");
        assertThat(config.getTracking().getRolling().getPassNoPoint().getName())
                .isEqualTo("pass_no_pv");
        assertThat(config.getTracking().getRolling().getDirectReverse()).isFalse();

        assertInvalid(json.replace("\"name\":\"rolling_direction\"", "\"name\":\"\""));
        assertInvalid(json.replace("\"pass_no_point\"", "\"missing_pass_no_point\""));
    }

    @Test
    void rejectsMissingConditionInvalidWindowAndDuplicateDevices() throws Exception {
        assertInvalid(validConfig().replace("\"start_condition\"", "\"missing_condition\""));
        assertInvalid(validConfig().replace("\"sample_count\":3", "\"sample_count\":1"));
        assertInvalid(validConfig().replace("\"code\":\"C1\"", "\"code\":\"U1\""));
        assertInvalid(validConfig().replace("\"remaining_length\":{\"name\":\"c1_length\"}",
                "\"missing_remaining_length\":{\"name\":\"c1_length\"}"));
        assertInvalid(validConfig().replace("[\"11\",\"91\"]", "[\"11\"]"));
        assertInvalid(validConfig().replace("\"type\":\"boolean\"", "\"type\":\"short\""));
        assertInvalid(validConfig().replace("\"false_index\":0", "\"false_index\":2"));
        assertInvalid(validConfig().replace("\"coiler_method\":{\"default\":false,\"false_index\":0}",
                "\"missing_coiler_method\":{\"default\":false,\"false_index\":0}"));
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
                + "\"coiler_method_def\":{"
                + "\"uncoiler\":{\"name\":[\"上开卷\",\"下开卷\"],\"code\":[\"11\",\"91\"]},"
                + "\"coiler\":{\"name\":[\"上卷取\",\"下卷取\"],\"code\":[\"19\",\"99\"]}},"
                + "\"points\":["
                + "{\"code\":\"U1\",\"name\":\"开卷机1\",\"side\":\"uncoiler\","
                + "\"coil_no\":{\"name\":\"u1_coil\"},"
                + "\"color_no\":{\"name\":\"u1_color\"},"
                + "\"remaining_length\":{\"name\":\"u1_length\"},"
                + "\"coiler_method\":{\"name\":\"u1_method\",\"type\":\"boolean\","
                + "\"default\":true,\"false_index\":0}},"
                + "{\"code\":\"C1\",\"name\":\"卷取机1\",\"side\":\"coiler\","
                + "\"coil_no\":{\"name\":\"c1_coil\"},"
                + "\"remaining_length\":{\"name\":\"c1_length\"},"
                + "\"coiler_method\":{\"default\":false,\"false_index\":0}}]}}";
    }
}
