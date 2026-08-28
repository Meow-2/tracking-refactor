package com.wisdri.tracking.infrastructure.service.feign.converter.trimming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.AbstractCubeApiTrackingConfigConverter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 切边跟踪 Cube 配置转换器，目录结构与 process 兼容。
 */
@Component
public class TrimmingCubeApiTrackingConfigConverter extends AbstractCubeApiTrackingConfigConverter {
    private static final String TECH_FIELD = "tech";

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.TRIMMING == trackingType;
    }

    @Override
    public TrackingConfig convert(String unitCode, CubeApiTreeNode typeNode) {
        JsonNode defaultNode = defaultNode(typeNode);
        if (defaultNode == null || !defaultNode.isObject()) {
            return null;
        }
        ObjectNode config = defaultNode.deepCopy();
        config.set("segments", convertSegments(typeNode.getChildren().get(TECH_FIELD)));
        return readConfig(unitCode, TrackingType.TRIMMING, config, TrimmingTrackingConfig.class);
    }

    private ArrayNode convertSegments(CubeApiTreeNode techNode) {
        ArrayNode segments = objectMapper.createArrayNode();
        if (techNode == null) {
            return segments;
        }
        for (CubeApiTreeNode segmentNode : techNode.getChildren().values()) {
            JsonNode segmentDefault = defaultNode(segmentNode);
            if (segmentDefault == null || !segmentDefault.isObject()) {
                continue;
            }
            ObjectNode segment = segmentDefault.deepCopy();
            segment.set("points", pointNames(segmentNode));
            segments.add(segment);
        }
        return segments;
    }

    private ArrayNode pointNames(CubeApiTreeNode segmentNode) {
        ArrayNode points = objectMapper.createArrayNode();
        for (Map.Entry<String, CubeApiTreeNode> point : segmentNode.getChildren().entrySet()) {
            if (isPointNode(point.getValue())) {
                ObjectNode converted = objectMapper.createObjectNode();
                converted.put("name", point.getKey());
                converted.put("type", pointType(point.getValue()));
                points.add(converted);
            }
        }
        return points;
    }
}
