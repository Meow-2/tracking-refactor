package com.wisdri.tracking.infrastructure.service.feign.converter.shear;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.CutSetting;
import com.wisdri.tracking.domain.model.config.shear.ShearMode;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearSettings;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.config.shear.ShearTypeCodes;
import com.wisdri.tracking.domain.model.config.shear.WelderShearSettings;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.AbstractCubeApiTrackingConfigConverter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用剪切跟踪 Cube 配置转换器。
 */
@Component
public class ShearCubeApiTrackingConfigConverter extends AbstractCubeApiTrackingConfigConverter {
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.SHEAR == trackingType;
    }

    @Override
    public TrackingConfig convert(String unitCode, CubeApiTreeNode typeNode) {
        JsonNode configNode = defaultNode(typeNode);
        if (configNode == null || !configNode.isObject()) {
            return null;
        }
        ShearTrackingConfig config = readConfig(
                unitCode, TrackingType.SHEAR, configNode, ShearTrackingConfig.class);
        validate(config);
        return config;
    }

    private void validate(ShearTrackingConfig config) {
        ShearTrackingSection tracking = config.getTracking();
        if (tracking == null) {
            throw new TrackingException("shear.tracking 不能为空");
        }
        if (tracking.getMode() != ShearMode.CONTINUOUS) {
            throw new TrackingException("当前仅支持 continuous 剪切模式");
        }
        if (tracking.getTailExperience() == null || tracking.getTailExperience().signum() < 0
                || tracking.getShearExperience() == null || tracking.getShearExperience().signum() < 0) {
            throw new TrackingException("shear 经验阈值不能为空或小于 0");
        }
        Set<String> names = new HashSet<>();
        validatePoints(tracking.getUncoilerShearPoint(), true, names);
        validatePoints(tracking.getCoilerShearPoint(), false, names);
        if (names.isEmpty()) {
            throw new TrackingException("shear 剪刀配置不能为空");
        }
    }

    private void validatePoints(List<ShearPointConfig> points,
                                boolean uncoilerSide,
                                Set<String> names) {
        if (points == null) {
            return;
        }
        for (ShearPointConfig point : points) {
            if (point == null || blank(point.getName()) || point.getType() == null
                    || point.getNormalPos() == null || blank(point.getPorTrCode())) {
                throw new TrackingException("shear 剪刀基础配置无效");
            }
            if (point.getType() != PointDataType.BOOLEAN) {
                throw new TrackingException("shear 剪切信号必须为 boolean: " + point.getName());
            }
            if (!names.add(point.getName())) {
                throw new TrackingException("shear 剪刀名称重复: " + point.getName());
            }
            validateTypeCodes(point);
            ShearSettings settings = point.getShearSettings();
            if (settings == null) {
                throw new TrackingException("shear_settings 不能为空: " + point.getName());
            }
            if (settings.getDefaultValue() == null && invalidPoint(point.getColorPoint())) {
                throw new TrackingException("continuous 剪刀颜色点无效: " + point.getName());
            }
            if (uncoilerSide) {
                if (settings.getDefaultValue() == null
                        || settings.getDefaultValue() == ShearKind.HEAD) {
                    validateCutSetting(settings.getHead(), point.getName(), "head");
                }
                if (settings.getDefaultValue() == null
                        || settings.getDefaultValue() == ShearKind.TAIL) {
                    validateCutSetting(settings.getTail(), point.getName(), "tail");
                }
            } else {
                if (settings.getDefaultValue() == null
                        || settings.getDefaultValue() == ShearKind.TAIL) {
                    validateWelder(settings.getFrontWelder(), point.getName(), "front_welder");
                }
                if (settings.getDefaultValue() == null
                        || settings.getDefaultValue() == ShearKind.HEAD) {
                    validateWelder(settings.getBehindWelder(), point.getName(), "behind_welder");
                }
            }
        }
    }

    private void validateTypeCodes(ShearPointConfig point) {
        ShearTypeCodes codes = point.getTypeCodes();
        if (codes == null || codes.getHead() == null || codes.getSlice() == null || codes.getTail() == null) {
            throw new TrackingException("type_codes 必须包含 head/slice/tail: " + point.getName());
        }
        Set<Integer> values = new HashSet<>();
        values.add(codes.getHead());
        values.add(codes.getSlice());
        values.add(codes.getTail());
        if (values.size() != 3) {
            throw new TrackingException("type_codes 不能重复: " + point.getName());
        }
    }

    private void validateCutSetting(CutSetting setting, String pointName, String type) {
        if (setting == null || invalidPoint(setting.getLength()) || invalidPoint(setting.getNumber())) {
            throw new TrackingException(type + " 长度或设定刀数点位无效: " + pointName);
        }
    }

    private void validateWelder(WelderShearSettings setting, String pointName, String type) {
        if (setting == null || invalidPoint(setting.getSamplePieces())
                || invalidPoint(setting.getScrapPieces()) || invalidWelderLength(setting)) {
            throw new TrackingException(type + " 参数点位无效: " + pointName);
        }
    }

    private boolean invalidWelderLength(WelderShearSettings setting) {
        return invalidPoint(setting.getLength())
                && (invalidPoint(setting.getSampleLength()) || invalidPoint(setting.getScrapLength()));
    }

    private boolean invalidPoint(PointConfig point) {
        return point == null || blank(point.getName());
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
