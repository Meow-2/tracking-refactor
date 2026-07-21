package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;

/**
 * Cube API 类型转换器的公共 JSON 和点位元数据处理能力。
 */
public abstract class AbstractCubeApiTrackingConfigConverter implements CubeApiTrackingConfigConverter {
    private static final String DEFAULT_FIELD = "default";
    private static final String DEFAULT_POINT_TYPE = "float";

    protected final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapperBuilder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    /**
     * 读取目录 data.default 中的基础配置。
     */
    protected JsonNode defaultNode(CubeApiTreeNode node) {
        JsonNode dataNode = node == null ? null : node.getData();
        if (dataNode == null || dataNode.isNull()) {
            return null;
        }
        JsonNode defaultNode = dataNode.path(DEFAULT_FIELD);
        return defaultNode.isMissingNode() ? null : defaultNode;
    }

    /**
     * 反序列化配置并补充机组与跟踪类型。
     */
    protected <T extends TrackingConfig> T readConfig(String unitCode,
                                                       TrackingType trackingType,
                                                       JsonNode configNode,
                                                       Class<T> configType) {
        try {
            T config = objectMapper.treeToValue(configNode, configType);
            config.setUnitCode(unitCode);
            config.setTrackingType(trackingType);
            return config;
        } catch (Exception e) {
            throw new TrackingException("转换 Cube API 跟踪配置失败: " + unitCode + " " + trackingType, e);
        }
    }

    /**
     * 判断字段是否为直属点位，排除目录和段元数据。
     */
    protected boolean isPointNode(CubeApiTreeNode node) {
        return node != null && (node.getItemType() == null || node.getItemType() == 2);
    }

    /**
     * 将 Cube valueType 归一化为领域点位类型编码。
     */
    protected String pointType(CubeApiTreeNode pointNode) {
        String valueType = valueType(pointNode);
        if (valueType == null || valueType.trim().isEmpty()) {
            return DEFAULT_POINT_TYPE;
        }
        return PointDataType.fromCode(valueType.trim()).getCode();
    }

    private String valueType(CubeApiTreeNode pointNode) {
        if (pointNode == null) {
            return null;
        }
        if (pointNode.getValueType() != null) {
            return pointNode.getValueType();
        }
        JsonNode data = pointNode.getData();
        JsonNode dataValueType = data == null ? null : data.path("valueType");
        return dataValueType != null && dataValueType.isTextual() ? dataValueType.asText() : null;
    }
}
