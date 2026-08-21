package com.wisdri.tracking.infrastructure.service.feign.converter.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.AbstractCubeApiTrackingConfigConverter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 状态跟踪 Cube 配置转换器。
 */
@Component
public class StatusCubeApiTrackingConfigConverter extends AbstractCubeApiTrackingConfigConverter {
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.STATUS == trackingType;
    }

    @Override
    public TrackingConfig convert(String unitCode, CubeApiTreeNode typeNode) {
        JsonNode configNode = defaultNode(typeNode);
        if (configNode == null || !configNode.isObject()) {
            return null;
        }
        StatusTrackingConfig config = readConfig(
                unitCode, TrackingType.STATUS, configNode, StatusTrackingConfig.class);
        validate(config);
        return config;
    }

    private void validate(StatusTrackingConfig config) {
        StatusTrackingSection tracking = config.getTracking();
        if (tracking == null) {
            throw new TrackingException("status.tracking 不能为空");
        }
        StartCondition condition = tracking.getStartCondition();
        if (condition == null || invalidPoint(condition.getPoint()) || condition.getThreshold() == null) {
            throw new TrackingException("status.start_condition 配置无效");
        }
        if (tracking.getSampleCount() == null || tracking.getSampleCount() < 2) {
            throw new TrackingException("status.sample_count 必须大于等于 2");
        }
        if (tracking.getMinLengthChange() == null || tracking.getMinLengthChange().signum() < 0) {
            throw new TrackingException("status.min_length_change 不能小于 0");
        }
        if (tracking.getPoints() == null || tracking.getPoints().isEmpty()) {
            throw new TrackingException("status.points 不能为空");
        }
        Set<String> codes = new HashSet<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            if (group == null || blank(group.getCode()) || blank(group.getName()) || group.getSide() == null
                    || invalidPoint(group.getCoilNo()) || invalidPoint(group.getRemainingLength())) {
                throw new TrackingException("status.points 设备组合配置无效");
            }
            if (!codes.add(group.getCode().toLowerCase(Locale.ROOT))) {
                throw new TrackingException("status.points 设备编码重复: " + group.getCode());
            }
        }
    }

    private boolean invalidPoint(PointConfig point) {
        return point == null || blank(point.getName());
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
