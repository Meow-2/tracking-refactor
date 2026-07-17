package com.wisdri.tracking.infrastructure.service.feign.converter.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.AbstractCubeApiTrackingConfigConverter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 过程跟踪 Cube 配置转换器。
 */
@Component
public class ProcessCubeApiTrackingConfigConverter extends AbstractCubeApiTrackingConfigConverter {
    private static final String TECH_FIELD = "tech";
    private static final String POINTS_FIELD = "points";

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.PROCESS == trackingType;
    }

    @Override
    public TrackingConfig convert(String unitCode, CubeApiTreeNode typeNode) {
        JsonNode defaultNode = defaultNode(typeNode);
        if (defaultNode == null || !defaultNode.isObject()) {
            return null;
        }
        ObjectNode config = defaultNode.deepCopy();
        config.set("segments", convertSegments(typeNode.getChildren().get(TECH_FIELD)));
        return readConfig(unitCode, TrackingType.PROCESS, config, ProcessTrackingConfig.class);
    }

    private ArrayNode convertSegments(CubeApiTreeNode techNode) {
        ArrayNode segments = objectMapper.createArrayNode();
        if (techNode == null) {
            return segments;
        }
        for (CubeApiTreeNode segmentNode : techNode.getChildren().values()) {
            if (segmentNode == null) {
                continue;
            }
            JsonNode segmentDefaultNode = defaultNode(segmentNode);
            if (segmentDefaultNode == null || !segmentDefaultNode.isObject()) {
                continue;
            }
            ObjectNode segment = segmentDefaultNode.deepCopy();
            segment.set(POINTS_FIELD, pointNames(segmentNode));
            segments.add(segment);
        }
        return segments;
    }

    private ArrayNode pointNames(CubeApiTreeNode segmentNode) {
        ArrayNode points = objectMapper.createArrayNode();
        for (Map.Entry<String, CubeApiTreeNode> pointField : segmentNode.getChildren().entrySet()) {
            if (isPointNode(pointField.getValue())) {
                ObjectNode point = objectMapper.createObjectNode();
                point.put("name", pointField.getKey());
                point.put("type", pointType(pointField.getValue()));
                points.add(point);
            }
        }
        return points;
    }
}
