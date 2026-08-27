package com.wisdri.tracking.infrastructure.service.feign.converter.shear;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.CutSetting;
import com.wisdri.tracking.domain.model.config.shear.GratingPointConfig;
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
import java.util.ArrayList;
import java.util.Collections;
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
        if (tracking.getMode() == null) {
            throw new TrackingException("shear.mode 不能为空");
        }
        if (tracking.getTailExperience() == null || tracking.getTailExperience().signum() < 0
                || tracking.getShearExperience() == null || tracking.getShearExperience().signum() < 0) {
            throw new TrackingException("shear 经验阈值不能为空或小于 0");
        }
        Set<String> names = new HashSet<>();
        validatePoints(tracking.getUncoilerShearPoint(), true, tracking.getMode(), names);
        validatePoints(tracking.getCoilerShearPoint(), false, tracking.getMode(), names);
        if (names.isEmpty()) {
            throw new TrackingException("shear 剪刀配置不能为空");
        }
    }

    private void validatePoints(List<ShearPointConfig> points,
                                boolean uncoilerSide,
                                ShearMode mode,
                                Set<String> names) {
        if (points == null) {
            return;
        }
        for (ShearPointConfig point : points) {
            if (point == null || blank(point.getName()) || point.getType() == null
                    || point.getNormalPos() == null) {
                throw new TrackingException("shear 剪刀基础配置无效");
            }
            List<String> deviceCodes = deviceCodes(point);
            if (deviceCodes.isEmpty() || deviceCodes.stream().anyMatch(this::blank)
                    || new HashSet<>(deviceCodes).size() != deviceCodes.size()) {
                throw new TrackingException("shear por_tr_codes 不能为空或重复: " + point.getName());
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
            if (settings.getDefaultValue() == null) {
                if (mode == ShearMode.CONTINUOUS && invalidPoint(point.getColorPoint())) {
                    throw new TrackingException("continuous 剪刀颜色点无效: " + point.getName());
                }
                if (mode == ShearMode.DISCONTINUOUS) {
                    validateGratings(point);
                }
            }
            if (uncoilerSide || mode == ShearMode.DISCONTINUOUS) {
                if (settings.getDefaultValue() == null
                        || settings.getDefaultValue() == ShearKind.HEAD) {
                    validateCutSetting(settings.getHead(), point.getName(), "head");
                }
                if (settings.getDefaultValue() == null
                        || settings.getDefaultValue() == ShearKind.TAIL) {
                    validateCutSetting(settings.getTail(), point.getName(), "tail");
                }
            } else {
                if (settings.getWelderPieces() != null && invalidPoint(settings.getWelderPieces())) {
                    throw new TrackingException("welder_pieces 点位无效: " + point.getName());
                }
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

    private void validateGratings(ShearPointConfig point) {
        List<GratingPointConfig> gratings = point.getGratingPoints();
        if (gratings == null || gratings.isEmpty()) {
            throw new TrackingException("discontinuous 光栅配置不能为空: " + point.getName());
        }
        Set<String> names = new HashSet<>();
        for (GratingPointConfig grating : gratings) {
            if (grating == null || blank(grating.getName())
                    || grating.getType() != PointDataType.BOOLEAN
                    || grating.getHasCoil() == null
                    || !names.add(grating.getName())) {
                throw new TrackingException("discontinuous 光栅配置无效或重复: " + point.getName());
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

    private List<String> deviceCodes(ShearPointConfig point) {
        if (point.getPorTrCodes() != null && !point.getPorTrCodes().isEmpty()) {
            return new ArrayList<>(point.getPorTrCodes());
        }
        return blank(point.getPorTrCode())
                ? Collections.emptyList() : Collections.singletonList(point.getPorTrCode());
    }
}
