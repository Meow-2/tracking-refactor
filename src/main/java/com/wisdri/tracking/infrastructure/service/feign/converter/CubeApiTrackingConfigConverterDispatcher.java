package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * Cube API 跟踪配置转换分发器。
 */
@Slf4j
@Component
public class CubeApiTrackingConfigConverterDispatcher {
    @Resource
    private TrackingProperties trackingProperties;

    @Resource
    private List<CubeApiTrackingConfigConverter> converters;

    /**
     * 查找当前机组，并将其各跟踪类型目录分发给对应转换器。
     */
    public Map<TrackingType, TrackingConfig> convert(CubeApiTreeResponse tree) {
        Map<TrackingType, TrackingConfig> configs = new EnumMap<>(TrackingType.class);
        if (tree == null) {
            return configs;
        }
        for (Map.Entry<String, CubeApiTreeNode> unitField : tree.getRoots().entrySet()) {
            if (sameUnit(unitField.getKey(), trackingProperties.getUnit()) && unitField.getValue() != null) {
                convertUnit(trackingProperties.getUnit(), unitField.getValue(), configs);
                break;
            }
        }
        return configs;
    }

    private void convertUnit(String unitCode,
                             CubeApiTreeNode unitNode,
                             Map<TrackingType, TrackingConfig> configs) {
        for (Map.Entry<String, CubeApiTreeNode> typeField : unitNode.getChildren().entrySet()) {
            TrackingType trackingType = parseTrackingType(typeField.getKey());
            if (trackingType == null || typeField.getValue() == null) {
                log.debug("跳过未知跟踪类型配置，unitCode={}, type={}", unitCode, typeField.getKey());
                continue;
            }
            Optional<CubeApiTrackingConfigConverter> converter = converter(trackingType);
            if (!converter.isPresent()) {
                log.debug("跳过未注册转换器的跟踪类型，unitCode={}, trackingType={}", unitCode, trackingType);
                continue;
            }
            try {
                TrackingConfig config = converter.get().convert(unitCode, typeField.getValue());
                if (config != null) {
                    configs.put(trackingType, config);
                }
            } catch (RuntimeException e) {
                log.warn("转换 Cube API 跟踪配置失败，unitCode={}, trackingType={}", unitCode, trackingType, e);
            }
        }
    }

    private Optional<CubeApiTrackingConfigConverter> converter(TrackingType trackingType) {
        if (converters == null) {
            return Optional.empty();
        }
        return converters.stream().filter(converter -> converter.support(trackingType)).findFirst();
    }

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

    private boolean sameUnit(String left, String right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        return left.equalsIgnoreCase(right);
    }
}
