package com.wisdri.tracking.infrastructure.service.feign.converter.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.AbstractCubeApiTrackingConfigConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次跟踪 Cube 配置转换器。
 */
@Component
public class BatchCubeApiTrackingConfigConverter extends AbstractCubeApiTrackingConfigConverter {
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.BATCH == trackingType;
    }

    @Override
    public TrackingConfig convert(String unitCode, CubeApiTreeNode batchNode) {
        JsonNode defaultNode = defaultNode(batchNode);
        if (defaultNode == null || !defaultNode.isObject()) {
            return null;
        }
        BatchTrackingConfig config = readConfig(
                unitCode, TrackingType.BATCH, defaultNode, BatchTrackingConfig.class);
        if (config.getTemplate() == null) {
            throw new TrackingException("batch.template 不能为空");
        }
        if (config.getSegments() == null || config.getSegments().isEmpty()) {
            throw new TrackingException("batch.segments 不能为空");
        }
        if (config.getTracking() != null
                && config.getTracking().getCurrentClearThreshold() != null
                && config.getTracking().getCurrentClearThreshold() < 0) {
            throw new TrackingException("batch.tracking.current_clear_threshold 不能小于 0");
        }

        List<String> templateCodes;
        try {
            templateCodes = config.getTemplate().resolveCodes();
        } catch (IllegalArgumentException e) {
            throw new TrackingException("batch.template 配置无效", e);
        }
        for (SegmentConfig segment : config.getSegments()) {
            enrichSegment(batchNode, templateCodes, segment);
        }
        return config;
    }

    private void enrichSegment(CubeApiTreeNode batchNode,
                               List<String> templateCodes,
                               SegmentConfig segment) {
        if (segment == null || blank(segment.getCode())) {
            throw new TrackingException("batch segment.code 不能为空");
        }
        Map<String, PointConfig> reference = null;
        for (String templateCode : templateCodes) {
            CubeApiTreeNode templateDirectory = requiredDirectory(batchNode, templateCode,
                    "找不到 batch 模板目录: " + templateCode);
            CubeApiTreeNode segmentDirectory = requiredDirectory(templateDirectory, segment.getCode(),
                    "找不到 batch segment 目录: template=" + templateCode + ", segment=" + segment.getCode());
            Map<String, PointConfig> parsed = parsePoints(segmentDirectory, segment, templateCode);
            if (reference == null) {
                reference = parsed;
            } else if (!samePointStructure(reference, parsed)) {
                throw new TrackingException("batch 模板点位结构不一致: template=" + templateCode
                        + ", segment=" + segment.getCode());
            }
        }
        segment.setPoints(mergePoints(segment.getPoints(), reference));
    }

    private Map<String, PointConfig> parsePoints(CubeApiTreeNode directory,
                                                 SegmentConfig segment,
                                                 String templateCode) {
        String prefix = replaceTemplate(segment.getCubeParsingPrefix(), templateCode);
        if (blank(prefix)) {
            throw new TrackingException("batch cube_parsing_prefix 不能为空: segment=" + segment.getCode());
        }
        Map<String, PointConfig> points = new LinkedHashMap<>();
        for (Map.Entry<String, CubeApiTreeNode> pointField : directory.getChildren().entrySet()) {
            if (!isPointNode(pointField.getValue())) {
                continue;
            }
            String physicalName = pointField.getKey();
            if (physicalName == null || !physicalName.startsWith(prefix)) {
                throw new TrackingException("batch 点位前缀不匹配: template=" + templateCode
                        + ", segment=" + segment.getCode() + ", point=" + physicalName + ", prefix=" + prefix);
            }
            String logicalName = physicalName.substring(prefix.length());
            if (blank(logicalName)) {
                throw new TrackingException("batch 点位移除前缀后名称为空: " + physicalName);
            }
            PointConfig point = PointConfig.builder()
                    .name(logicalName)
                    .type(PointDataType.fromCode(pointType(pointField.getValue())))
                    .build();
            PointConfig previous = points.put(logicalName, point);
            if (previous != null && previous.getType() != point.getType()) {
                throw new TrackingException("batch 同名点位类型冲突: " + logicalName);
            }
        }
        return points;
    }

    private List<PointConfig> mergePoints(List<PointConfig> explicit, Map<String, PointConfig> parsed) {
        Map<String, PointConfig> merged = new LinkedHashMap<>();
        if (explicit != null) {
            for (PointConfig point : explicit) {
                if (point != null && !blank(point.getName())) {
                    merged.put(point.getName(), point);
                }
            }
        }
        if (parsed != null) {
            for (Map.Entry<String, PointConfig> entry : parsed.entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return new ArrayList<>(merged.values());
    }

    private boolean samePointStructure(Map<String, PointConfig> left, Map<String, PointConfig> right) {
        if (!left.keySet().equals(right.keySet())) {
            return false;
        }
        for (String pointName : left.keySet()) {
            if (left.get(pointName).getType() != right.get(pointName).getType()) {
                return false;
            }
        }
        return true;
    }

    private CubeApiTreeNode requiredDirectory(CubeApiTreeNode parent, String code, String message) {
        if (parent != null && code != null) {
            for (Map.Entry<String, CubeApiTreeNode> child : parent.getChildren().entrySet()) {
                if (code.equalsIgnoreCase(child.getKey()) && child.getValue() != null) {
                    return child.getValue();
                }
            }
        }
        throw new TrackingException(message);
    }

    private String replaceTemplate(String value, String templateCode) {
        return value == null ? null : value.replace("{template}", templateCode);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
