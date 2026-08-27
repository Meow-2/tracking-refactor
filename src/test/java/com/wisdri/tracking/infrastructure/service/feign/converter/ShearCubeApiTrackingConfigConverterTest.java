package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.shear.ShearCubeApiTrackingConfigConverter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShearCubeApiTrackingConfigConverterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ShearCubeApiTrackingConfigConverter converter =
            new ShearCubeApiTrackingConfigConverter();

    @Test
    void convertsTypeCodesWithoutDependingOnUnitOrDeviceNames() throws Exception {
        ShearTrackingConfig config = (ShearTrackingConfig) converter.convert(
                "LINE-X", node(validConfig()));

        assertThat(config.getUnitCode()).isEqualTo("LINE-X");
        assertThat(config.getTrackingType()).isEqualTo(TrackingType.SHEAR);
        assertThat(config.getTracking().getUncoilerShearPoint()).hasSize(1);
        assertThat(config.getTracking().getUncoilerShearPoint().get(0).getDeviceCode())
                .isEqualTo("feed-device-x");
        assertThat(config.getTracking().getUncoilerShearPoint().get(0).getTypeCodes().getHead())
                .isEqualTo("711");
        assertThat(config.getTracking().getCoilerShearPoint().get(0).getTypeCodes().getTail())
                .isEqualTo("939");
    }

    @Test
    void convertsCurrentCp1ConfigurationExample() throws Exception {
        String config = new String(Files.readAllBytes(Paths.get("docs/config/CP1/shear.json")),
                StandardCharsets.UTF_8);

        ShearTrackingConfig converted = (ShearTrackingConfig) converter.convert("CP1", node(config));

        assertThat(converted.getTracking().getUncoilerShearPoint()).hasSize(2);
        assertThat(converted.getTracking().getCoilerShearPoint()).hasSize(1);
        assertThat(converted.getTracking().getUncoilerShearPoint().get(0).getTypeCodes().getHead())
                    .isEqualTo("111");
        assertThat(converted.getTracking().getCoilerShearPoint().get(0).getTypeCodes().getTail())
                    .isEqualTo("909");
        assertThat(converted.getTracking().getCoilerShearPoint().get(0)
                .getShearSettings().getWelderPieces().getName())
                .isEqualTo("exit_shear_weld_seam_scrap_pieces");
    }

    @Test
    void acceptsOrderedDeviceCodesAndKeepsLegacySingleCode() throws Exception {
        String plural = validConfig().replace(
                "\"device_code\":\"take-device-x\"",
                "\"device_codes\":[\"take-device-a\",\"take-device-x\"]");

        ShearTrackingConfig pluralConfig = (ShearTrackingConfig) converter.convert("LINE-X", node(plural));
        ShearTrackingConfig legacyConfig = (ShearTrackingConfig) converter.convert("LINE-X", node(validConfig()));

        assertThat(pluralConfig.getTracking().getCoilerShearPoint().get(0).getDeviceCodes())
                .containsExactly("take-device-a", "take-device-x");
        assertThat(legacyConfig.getTracking().getCoilerShearPoint().get(0).getDeviceCode())
                .isEqualTo("take-device-x");
    }

    @Test
    void convertsContinuousLineExamplesWithSeparateSampleAndScrapLengths() throws Exception {
        for (String unit : new String[]{"DCL1", "FCL1"}) {
            String json = new String(Files.readAllBytes(
                    Paths.get("docs/config/" + unit + "/shear.json")), StandardCharsets.UTF_8);

            ShearTrackingConfig converted = (ShearTrackingConfig) converter.convert(unit, node(json));

            assertThat(converted.getTracking().getUncoilerShearPoint()).hasSize(2);
            assertThat(converted.getTracking().getCoilerShearPoint()).hasSize(1);
            assertThat(converted.getTracking().getCoilerShearPoint().get(0)
                    .getShearSettings().getFrontWelder().getSampleLength()).isNotNull();
            assertThat(converted.getTracking().getCoilerShearPoint().get(0)
                    .getShearSettings().getFrontWelder().getScrapLength()).isNotNull();
            assertThat(converted.getTracking().getCoilerShearPoint().get(0).getDeviceCodes())
                    .containsExactly("tr1", "tr2");
            assertThat(converted.getTracking().getCoilerShearPoint().get(0)
                    .getShearSettings().getWelderPieces()).isNotNull();
        }
    }

    @Test
    void convertsDiscontinuousLineExamplesWithGratingAndCutSettings() throws Exception {
        for (String unit : new String[]{"CBL1", "CSL1"}) {
            String json = new String(Files.readAllBytes(
                    Paths.get("docs/config/" + unit + "/shear.json")), StandardCharsets.UTF_8);

            ShearTrackingConfig converted = (ShearTrackingConfig) converter.convert(unit, node(json));

            assertThat(converted.getTracking().getMode().name()).isEqualTo("DISCONTINUOUS");
            assertThat(converted.getTracking().getUncoilerShearPoint()).isNotEmpty();
            assertThat(converted.getTracking().getUncoilerShearPoint().get(0).getGratingPoints())
                    .isNotEmpty();
            assertThat(converted.getTracking().getUncoilerShearPoint().get(0)
                    .getShearSettings().getHead().getNumber()).isNotNull();
        }
    }

    @Test
    void rejectsIncompleteModeConfigDuplicateNamesAndInvalidCodes() throws Exception {
        assertInvalid(validConfig().replace("\"continuous\"", "\"discontinuous\""));
        assertInvalid(validConfig().replace("\"exit-cut-x\"", "\"entry-cut-x\""));
        assertInvalid(validConfig().replace("\"slice\":\"715\"", "\"slice\":\"711\""));
        assertInvalid(validConfig().replace("\"tail_experience\":50", "\"tail_experience\":-1"));
        assertInvalid(validConfig().replace("\"number\":{\"name\":\"head-number\"},", ""));
    }

    @Test
    void acceptsDefaultSliceWithoutColorOrLengthSettings() throws Exception {
        String json = validConfig()
                .replace("\"shear_settings\":{\"head\":{\"number\":{\"name\":\"head-number\"},\"length\":{\"name\":\"head-length\"}},\"tail\":{\"number\":{\"name\":\"tail-number\"},\"length\":{\"name\":\"tail-length\"}}},\"color_point\":{\"name\":\"entry-color\"}",
                        "\"shear_settings\":{\"default\":\"slice\"}")
                .replace("\"shear_settings\":{\"front_welder\":{\"sample_pieces\":{\"name\":\"front-sample\"},\"scrap_pieces\":{\"name\":\"front-scrap\"},\"length\":{\"name\":\"front-length\"}},\"behind_welder\":{\"sample_pieces\":{\"name\":\"rear-sample\"},\"scrap_pieces\":{\"name\":\"rear-scrap\"},\"length\":{\"name\":\"rear-length\"}}},\"color_point\":{\"name\":\"exit-color\"}",
                        "\"shear_settings\":{\"default\":\"slice\"}");

        ShearTrackingConfig config = (ShearTrackingConfig) converter.convert("LINE-X", node(json));

        assertThat(config.getTracking().getUncoilerShearPoint().get(0)
                .getShearSettings().getDefaultValue().name()).isEqualTo("SLICE");
    }

    private void assertInvalid(String json) throws Exception {
        assertThatThrownBy(() -> converter.convert("LINE-X", node(json)))
                .isInstanceOf(TrackingException.class);
    }

    private CubeApiTreeNode node(String config) throws Exception {
        return objectMapper.readValue("{\"data\":{\"default\":" + config + "}}", CubeApiTreeNode.class);
    }

    private String validConfig() {
        return "{\"enable\":true,\"mqtt_topic\":\"line_x_shear\",\"tracking\":{"
                + "\"point_prefix\":\"/line-x/shear/\",\"mode\":\"continuous\","
                + "\"tail_experience\":50,\"shear_experience\":100,"
                + "\"uncoiler_shear_point\":[{\"name\":\"entry-cut-x\",\"type\":\"boolean\","
                + "\"type_codes\":{\"head\":\"711\",\"slice\":\"715\",\"tail\":\"719\"},"
                + "\"normal_pos\":true,\"device_code\":\"feed-device-x\","
                + "\"shear_settings\":{\"head\":{\"number\":{\"name\":\"head-number\"},\"length\":{\"name\":\"head-length\"}},"
                + "\"tail\":{\"number\":{\"name\":\"tail-number\"},\"length\":{\"name\":\"tail-length\"}}},"
                + "\"color_point\":{\"name\":\"entry-color\"}}],"
                + "\"coiler_shear_point\":[{\"name\":\"exit-cut-x\",\"type\":\"boolean\","
                + "\"type_codes\":{\"head\":\"931\",\"slice\":\"935\",\"tail\":\"939\"},"
                + "\"normal_pos\":true,\"device_code\":\"take-device-x\","
                + "\"shear_settings\":{\"front_welder\":{"
                + "\"sample_pieces\":{\"name\":\"front-sample\"},"
                + "\"scrap_pieces\":{\"name\":\"front-scrap\"},"
                + "\"length\":{\"name\":\"front-length\"}},"
                + "\"behind_welder\":{"
                + "\"sample_pieces\":{\"name\":\"rear-sample\"},"
                + "\"scrap_pieces\":{\"name\":\"rear-scrap\"},"
                + "\"length\":{\"name\":\"rear-length\"}}},"
                + "\"color_point\":{\"name\":\"exit-color\"}}]}}";
    }
}
