package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingConfig;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.ironloss.IronLossCubeApiTrackingConfigConverter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class IronLossCubeApiTrackingConfigConverterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IronLossCubeApiTrackingConfigConverter converter =
            new IronLossCubeApiTrackingConfigConverter();

    @Test
    void convertsFixedTrackingPointsAndTechParameters() throws Exception {
        IronLossTrackingConfig config = converter.convert("FCL1", node("1"));

        assertThat(config.getTracking().getCoilNo().getName()).isEqualTo("coil_no");
        assertThat(config.getTracking().getStartCondition().getThreshold())
                .isEqualByComparingTo(new BigDecimal("0.1"));
        assertThat(config.getSegments()).singleElement().satisfies(segment -> {
            assertThat(segment.getCode()).isEqualTo("iron_loss");
            assertThat(segment.getCellCodeValue()).isEqualTo(1);
            assertThat(segment.getPoints()).extracting("name", "type")
                    .containsExactly(tuple("thickness", PointDataType.FLOAT),
                            tuple("iron_loss", PointDataType.FLOAT));
        });
    }

    @Test
    void convertsCellCodePointConfiguration() throws Exception {
        IronLossTrackingConfig config = converter.convert("FCL1", node("{\"name\":\"cell_code\",\"type\":\"short\"}"));

        assertThat(config.getSegments().get(0).getCellCodePoint().getName()).isEqualTo("cell_code");
        assertThat(config.getSegments().get(0).getCellCodeValue()).isNull();
    }

    @Test
    void rejectsMissingFixedTrackingPoint() throws Exception {
        CubeApiTreeNode node = node("1");
        node.getChildren().get("tracking").getChildren().remove("length");

        assertThatThrownBy(() -> converter.convert("FCL1", node))
                .isInstanceOf(TrackingException.class)
                .hasMessageContaining("固定钢卷号或长度点位配置无效");
    }

    private CubeApiTreeNode node(String cellCode) throws Exception {
        String json = "{\"data\":{\"default\":{\"enable\":true,"
                + "\"mqtt_topic\":\"fcl1_ironloss_tracking\",\"tracking\":{"
                + "\"point_prefix\":\"/aygg_tracking/fcl1/ironloss/tracking/\","
                + "\"coil_no\":{\"name\":\"coil_no\",\"type\":\"string\"},"
                + "\"length\":{\"name\":\"length\",\"type\":\"float\"},"
                + "\"start_condition\":{\"point\":{\"name\":\"length\",\"type\":\"float\"},"
                + "\"threshold\":0.1}}}},"
                + "\"tracking\":{\"coil_no\":{\"itemType\":2,\"valueType\":\"string\"},"
                + "\"length\":{\"itemType\":2,\"valueType\":\"float\"}},"
                + "\"tech\":{\"iron_loss\":{\"data\":{\"default\":{"
                + "\"code\":\"iron_loss\",\"name\":\"铁损\","
                + "\"point_prefix\":\"/aygg_tracking/fcl1/ironloss/tech/iron_loss/\","
                + "\"cell_code\":" + cellCode + "}},"
                + "\"thickness\":{\"itemType\":2,\"valueType\":\"float\"},"
                + "\"iron_loss\":{\"itemType\":2,\"valueType\":\"float\"}}}}";
        return objectMapper.readValue(json, CubeApiTreeNode.class);
    }
}
