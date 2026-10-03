package com.wisdri.tracking.infrastructure.service.feign.converter.ironloss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossSegmentConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingSection;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.AbstractCubeApiTrackingConfigConverter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 铁损配置转换器：tracking 定义固定点位，tech 的各段定义动态列。 */
@Component
public class IronLossCubeApiTrackingConfigConverter extends AbstractCubeApiTrackingConfigConverter {
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.IRONLOSS == trackingType;
    }

    @Override
    public IronLossTrackingConfig convert(String unitCode, CubeApiTreeNode typeNode) {
        JsonNode defaultConfig = defaultNode(typeNode);
        if (defaultConfig == null || !defaultConfig.isObject()) {
            return null;
        }
        CubeApiTreeNode trackingDirectory = typeNode.getChildren().get("tracking");
        CubeApiTreeNode techDirectory = typeNode.getChildren().get("tech");
        if (trackingDirectory == null || techDirectory == null) {
            throw new TrackingException("ironloss 缺少 tracking 或 tech 目录");
        }
        ObjectNode configNode = defaultConfig.deepCopy();
        configNode.set("segments", segments(techDirectory));
        IronLossTrackingConfig config = readConfig(
                unitCode, TrackingType.IRONLOSS, configNode, IronLossTrackingConfig.class);
        validate(config, trackingDirectory);
        return config;
    }

    /** tech 下配置了 data.default 的目录才构成工艺段。 */
    private ArrayNode segments(CubeApiTreeNode techDirectory) {
        ArrayNode segments = objectMapper.createArrayNode();
        for (CubeApiTreeNode segmentDirectory : techDirectory.getChildren().values()) {
            JsonNode segmentDefault = defaultNode(segmentDirectory);
            if (segmentDefault == null || !segmentDefault.isObject()) {
                continue;
            }
            ObjectNode segment = segmentDefault.deepCopy();
            normalizeCellCode(segment, "ironloss");
            segment.set("points", directPointNames(segmentDirectory));
            segments.add(segment);
        }
        return segments;
    }

    /** 检查固定点位确实属于 tracking 目录，防止配置错误静默跳过全部消息。 */
    private void validate(IronLossTrackingConfig config, CubeApiTreeNode trackingDirectory) {
        IronLossTrackingSection tracking = config.getTracking();
        if (tracking == null || blank(tracking.getPointPrefix())
                || !declared(trackingDirectory, tracking.getCoilNo())
                || !declared(trackingDirectory, tracking.getLength())) {
            throw new TrackingException("ironloss 固定钢卷号或长度点位配置无效");
        }
        if (tracking.getStartCondition() != null
                && (!declared(trackingDirectory, tracking.getStartCondition().getPoint())
                || tracking.getStartCondition().getThreshold() == null)) {
            throw new TrackingException("ironloss.start_condition 配置无效");
        }
        if (config.getSegments() == null || config.getSegments().isEmpty()) {
            throw new TrackingException("ironloss 缺少已配置的 tech 工艺段");
        }
        Set<String> segmentCodes = new HashSet<>();
        for (IronLossSegmentConfig segment : config.getSegments()) {
            if (segment == null || blank(segment.getCode()) || blank(segment.getPointPrefix())
                    || !segmentCodes.add(segment.getCode().toLowerCase(Locale.ROOT))) {
                throw new TrackingException("ironloss 工艺段编码或点位前缀无效");
            }
            Set<String> columns = new HashSet<>();
            for (PointConfig point : segment.getPoints()) {
                String name = point.getName();
                if (blank(name) || point.getType() == null || !columns.add(name.toLowerCase(Locale.ROOT))) {
                    throw new TrackingException("ironloss 工艺段点位名称或类型无效: " + segment.getCode());
                }
                if ("coil_no".equalsIgnoreCase(name) || "head_length".equalsIgnoreCase(name)
                        || "repeat_prod_no".equalsIgnoreCase(name)) {
                    throw new TrackingException("ironloss 参数与固定列重名: " + name);
                }
            }
        }
    }

    private boolean declared(CubeApiTreeNode directory, PointConfig point) {
        if (point == null || blank(point.getName())) {
            return false;
        }
        for (Map.Entry<String, CubeApiTreeNode> entry : directory.getChildren().entrySet()) {
            if (isPointNode(entry.getValue()) && point.getName().equals(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
