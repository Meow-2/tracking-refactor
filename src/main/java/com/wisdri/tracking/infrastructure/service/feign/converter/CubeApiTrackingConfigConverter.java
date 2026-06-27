package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final String POINT_NAME_FIELD = "name";
    private static final String POINT_TYPE_FIELD = "type";
    private static final String VALUE_TYPE_FIELD = "valueType";
    private static final String DEFAULT_POINT_TYPE = "float";
    private static final String ITEM_TYPE_FIELD = "itemType";
    private static final Set<String> METADATA_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "id", "pid", "code", "name", "itemType", "cubeKey", "path", "anaItemMeta", "meta", "collectItemId",
            "valueType", "unit", "tag", "measurement", "mark", "valid", "varAttr1", "varAttr2"
    )));

    /**
     * JSON 节点构造器。
     */
    private final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapperBuilder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    /**
     * 当前实例跟踪配置。
     */
    @Resource
    private TrackingProperties trackingProperties;

    /**
     * 将 Cube API 配置树转换为可写入 Redis 的跟踪配置。
     */
    public Map<TrackingType, TrackingConfig> convert(JsonNode tree) {
        Map<TrackingType, TrackingConfig> configs = new EnumMap<>(TrackingType.class);
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
            if (sameUnit(unitCode, trackingProperties.getUnit())) {
                convertUnit(trackingProperties.getUnit(), unitNode, configs);
                return configs;
            }
        }
        return configs;
    }

    /**
     * 转换单个机组下的跟踪配置。
     */
    private void convertUnit(String unitCode, JsonNode unitNode, Map<TrackingType, TrackingConfig> configs) {
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
                configs.put(trackingType, readConfig(unitCode, trackingType, config));
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
            if (isPointField(fieldName, segmentNode.get(fieldName))) {
                ObjectNode point = objectMapper.createObjectNode();
                point.put(POINT_NAME_FIELD, fieldName);
                point.put(POINT_TYPE_FIELD, pointType(segmentNode.get(fieldName)));
                points.add(point);
            }
        }
        return points;
    }

    /**
     * 判断字段是否为点位节点，排除 paraRange=3 返回的目录/段元数据字段。
     */
    private boolean isPointField(String fieldName, JsonNode fieldNode) {
        if (DATA_FIELD.equals(fieldName) || METADATA_FIELDS.contains(fieldName)
                || fieldNode == null || !fieldNode.isObject()) {
            return false;
        }
        JsonNode itemType = fieldNode.path(ITEM_TYPE_FIELD);
        return itemType.isMissingNode() || itemType.asInt() == 2;
    }

    /**
     * 从 Cube API 点位元数据读取点位类型。
     */
    private String pointType(JsonNode pointNode) {
        String valueType = valueType(pointNode);
        if (valueType == null || valueType.trim().isEmpty()) {
            return DEFAULT_POINT_TYPE;
        }

        String normalized = valueType.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("bool")) {
            return "bool";
        }
        if (normalized.contains("string") || normalized.contains("char") || normalized.contains("text")) {
            return "string";
        }
        if (normalized.contains("int") || normalized.contains("long") || normalized.contains("short")) {
            return "int";
        }
        return DEFAULT_POINT_TYPE;
    }

    /**
     * 兼容 valueType 位于点位节点或 data 节点两种结构。
     */
    private String valueType(JsonNode pointNode) {
        if (pointNode == null || pointNode.isMissingNode() || pointNode.isNull()) {
            return null;
        }
        JsonNode directValueType = pointNode.path(VALUE_TYPE_FIELD);
        if (directValueType.isTextual()) {
            return directValueType.asText();
        }
        JsonNode dataValueType = pointNode.path(DATA_FIELD).path(VALUE_TYPE_FIELD);
        return dataValueType.isTextual() ? dataValueType.asText() : null;
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

    private TrackingConfig readConfig(String unitCode, TrackingType trackingType, JsonNode configNode) {
        Class<? extends TrackingConfig> configType = configType(trackingType);
        if (configType == null) {
            throw new TrackingException("不支持的跟踪配置类型: " + trackingType);
        }
        try {
            TrackingConfig config = objectMapper.treeToValue(configNode, configType);
            config.setUnitCode(unitCode);
            config.setTrackingType(trackingType);
            return config;
        } catch (Exception e) {
            throw new TrackingException("转换 Cube API 跟踪配置失败: " + unitCode + " " + trackingType, e);
        }
    }

    private Class<? extends TrackingConfig> configType(TrackingType trackingType) {
        if (trackingType == TrackingType.PROCESS) {
            return ProcessTrackingConfig.class;
        }
        return null;
    }

    private boolean sameUnit(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return right != null && left.equalsIgnoreCase(right);
    }
}
