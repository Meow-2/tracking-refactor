package com.wisdri.tracking.infrastructure.service.feign.converter.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodConfig;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodDefinition;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodDefinitions;
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
        validateCoilerMethodDefinitions(tracking.getCoilerMethodDef());
        Set<String> codes = new HashSet<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            if (group == null || blank(group.getCode()) || blank(group.getName()) || group.getSide() == null
                    || invalidPoint(group.getCoilNo()) || invalidPoint(group.getRemainingLength())) {
                throw new TrackingException("status.points 设备组合配置无效");
            }
            if (group.getColorNo() != null && invalidPoint(group.getColorNo())) {
                throw new TrackingException("status.points 色号点位配置无效: " + group.getCode());
            }
            validateCoilerMethod(group, tracking.getCoilerMethodDef() != null);
            if (!codes.add(group.getCode().toLowerCase(Locale.ROOT))) {
                throw new TrackingException("status.points 设备编码重复: " + group.getCode());
            }
        }
    }

    private void validateCoilerMethodDefinitions(CoilerMethodDefinitions definitions) {
        if (definitions == null) {
            return;
        }
        validateCoilerMethodDefinition("uncoiler", definitions.getUncoiler());
        validateCoilerMethodDefinition("coiler", definitions.getCoiler());
    }

    private void validateCoilerMethodDefinition(String side, CoilerMethodDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getCode() == null
                || definition.getName().size() != 2 || definition.getCode().size() != 2
                || definition.getName().stream().anyMatch(this::blank)
                || definition.getCode().stream().anyMatch(this::blank)) {
            throw new TrackingException("status.coiler_method_def." + side
                    + " 的 name/code 必须各配置 true、false 两个非空值");
        }
    }

    private void validateCoilerMethod(StatusPointGroup group, boolean definitionsConfigured) {
        CoilerMethodConfig method = group.getCoilerMethod();
        if (!definitionsConfigured && method == null) {
            return;
        }
        if (!definitionsConfigured || method == null || method.getDefaultValue() == null) {
            throw new TrackingException("status.points.coiler_method 配置无效: " + group.getCode());
        }
        if (method.getFalseIndex() != null
                && method.getFalseIndex() != 0 && method.getFalseIndex() != 1) {
            throw new TrackingException("status.points.coiler_method.false_index 只能配置 0 或 1: "
                    + group.getCode());
        }
        boolean hasName = !blank(method.getName());
        boolean hasType = method.getType() != null;
        if (hasName != hasType || (hasType && method.getType() != PointDataType.BOOLEAN)) {
            throw new TrackingException("status.points.coiler_method 点位必须同时配置 name 和 boolean type: "
                    + group.getCode());
        }
    }

    private boolean invalidPoint(PointConfig point) {
        return point == null || blank(point.getName());
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
