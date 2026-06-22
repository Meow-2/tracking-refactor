package com.wisdri.tracking.infrastructure.repository.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.config.ConvertedTrackingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cube API 配置树转换器。
 */
@Slf4j
@Component
public class CubeApiTrackingConfigConverter {
    private static final String DATA_FIELD = "data";
    private static final String DEFAULT_FIELD = "default";
    private static final String TECH_FIELD = "tech";
    private static final String POINTS_FIELD = "points";

    /**
     * JSON 节点构造器。
     */
    private final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapper();

    /**
     * 将 Cube API 配置树转换为可写入 Redis 的跟踪配置。
     */
    public List<ConvertedTrackingConfig> convert(JsonNode tree) {
        List<ConvertedTrackingConfig> configs = new ArrayList<>();
        if (tree == null || !tree.isObject()) {
            return configs;
        }

        Iterator<Map.Entry<String, JsonNode>> unitFields = tree.fields();
        while (unitFields.hasNext()) {
            Map.Entry<String, JsonNode> unitField = unitFields.next();
            String unitCode = unitField.getKey();
            JsonNode unitNode = unitField.getValue();
            if (DATA_FIELD.equals(unitCode) || unitNode == null || !unitNode.isObject()) {
                continue;
            }
            convertUnit(unitCode, unitNode, configs);
        }
        return configs;
    }

    /**
     * 转换单个机组下的跟踪配置。
     */
    private void convertUnit(String unitCode, JsonNode unitNode, List<ConvertedTrackingConfig> configs) {
        Iterator<Map.Entry<String, JsonNode>> typeFields = unitNode.fields();
        while (typeFields.hasNext()) {
            Map.Entry<String, JsonNode> typeField = typeFields.next();
            String typeName = typeField.getKey();
            JsonNode typeNode = typeField.getValue();
            if (DATA_FIELD.equals(typeName) || typeNode == null || !typeNode.isObject()) {
                continue;
            }

            TrackingType trackingType = parseTrackingType(typeName);
            if (trackingType == null) {
                log.debug("跳过未知跟踪类型配置，unitCode={}, type={}", unitCode, typeName);
                continue;
            }

            JsonNode defaultNode = defaultNode(typeNode);
            if (defaultNode == null || !defaultNode.isObject()) {
                continue;
            }

            try {
                JsonNode config = convertConfig(trackingType, typeNode, defaultNode);
                configs.add(new ConvertedTrackingConfig(unitCode, trackingType, config));
            } catch (RuntimeException e) {
                log.warn("转换 Cube API 跟踪配置失败，unitCode={}, trackingType={}", unitCode, trackingType, e);
            }
        }
    }

    /**
     * 按跟踪类型转换配置。
     */
    private JsonNode convertConfig(TrackingType trackingType, JsonNode typeNode, JsonNode defaultNode) {
        ObjectNode config = defaultNode.deepCopy();
        if (trackingType == TrackingType.PROCESS) {
            config.set("segments", convertProcessSegments(typeNode.path(TECH_FIELD)));
        }
        return config;
    }

    /**
     * 转换过程跟踪工艺段配置。
     */
    private ArrayNode convertProcessSegments(JsonNode techNode) {
        ArrayNode segments = objectMapper.createArrayNode();
        if (techNode == null || !techNode.isObject()) {
            return segments;
        }

        Iterator<Map.Entry<String, JsonNode>> segmentFields = techNode.fields();
        while (segmentFields.hasNext()) {
            Map.Entry<String, JsonNode> segmentField = segmentFields.next();
            String segmentKey = segmentField.getKey();
            JsonNode segmentNode = segmentField.getValue();
            if (DATA_FIELD.equals(segmentKey) || segmentNode == null || !segmentNode.isObject()) {
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

    /**
     * 提取工艺段点位名。
     */
    private ArrayNode pointNames(JsonNode segmentNode) {
        ArrayNode points = objectMapper.createArrayNode();
        Iterator<String> fieldNames = segmentNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!DATA_FIELD.equals(fieldName)) {
                points.add(fieldName);
            }
        }
        return points;
    }

    /**
     * 读取节点默认配置。
     */
    private JsonNode defaultNode(JsonNode node) {
        JsonNode dataNode = node.path(DATA_FIELD);
        if (dataNode.isMissingNode() || dataNode.isNull()) {
            return null;
        }
        JsonNode defaultNode = dataNode.path(DEFAULT_FIELD);
        return defaultNode.isMissingNode() ? null : defaultNode;
    }

    /**
     * 解析跟踪类型。
     */
    private TrackingType parseTrackingType(String typeName) {
        if (typeName == null) {
            return null;
        }
        try {
            return TrackingType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
