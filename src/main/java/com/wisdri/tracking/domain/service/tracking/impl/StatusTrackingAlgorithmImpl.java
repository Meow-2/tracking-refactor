package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodConfig;
import com.wisdri.tracking.domain.model.config.status.CoilerMethodDefinition;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.repository.quality.QualityRepository;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 根据连续多帧剩余长度变化识别当前运行的开卷机和卷取机。
 */
@Component
@Slf4j
public class StatusTrackingAlgorithmImpl implements TrackingAlgorithm<StatusResult> {
    private static final List<DeviceSide> RESULT_ORDER = Arrays.asList(
            DeviceSide.UNCOILER, DeviceSide.COILER);

    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    @Resource
    private TrackingStepLogger trackingStepLogger;

    @Resource
    private QualityRepository qualityRepository;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.STATUS == trackingType;
    }

    @Override
    public List<StatusResult> calculate(TrackingInput input) {
        validateInput(input);
        Optional<StatusTrackingConfig> configOptional = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(), TrackingType.STATUS, StatusTrackingConfig.class);
        trackingStepLogger.log(input, "计算开始", TrackingStepLogger.details(
                "configPresent", configOptional.isPresent()));
        if (!configOptional.isPresent()) {
            return new ArrayList<>();
        }

        StatusTrackingConfig config = configOptional.get();
        StatusTrackingSection tracking = config.getTracking();
        validateConfig(tracking);
        BigDecimal startValue = startConditionValue(input.getLatestSnapshot(), tracking);
        boolean started = started(startValue, tracking.getStartCondition());
        trackingStepLogger.log(input, "启动条件检查", TrackingStepLogger.details(
                "actual", startValue,
                "threshold", tracking.getStartCondition().getThreshold(),
                "passed", started));

        Instant generatedAt = Instant.now();
        List<StatusResult> results = new ArrayList<>();
        Map<String, StatusCandidateRuntime> candidates = started
                ? updateCandidates(input, tracking, generatedAt, results)
                : new LinkedHashMap<>();
        Map<DeviceSide, SelectedCandidate> selected = started
                ? selectCandidates(input, tracking, candidates)
                : new LinkedHashMap<>();
        Map<DeviceSide, StatusCurrentRuntime> current = current(input, selected);
        runtimeRepositoryDispatcher.saveRuntime(StatusTrackingRuntime.builder()
                .unitCode(input.getUnitCode())
                .trackingType(TrackingType.STATUS)
                .receivedAt(input.getLatestSnapshot() == null
                        ? null : input.getLatestSnapshot().getReceivedAt())
                .startConditionPointValue(startValue)
                .candidates(candidates)
                .current(current)
                .build());
        trackingStepLogger.log(input, "计算完成", TrackingStepLogger.details(
                "started", started,
                "uncoiler", current.get(DeviceSide.UNCOILER),
                "coiler", current.get(DeviceSide.COILER),
                "currentCount", current.size(),
                "resultCount", results.size()));
        return results;
    }

    private Map<String, StatusCandidateRuntime> updateCandidates(
            TrackingInput input,
            StatusTrackingSection tracking,
            Instant generatedAt,
            List<StatusResult> results) {
        Map<String, StatusCandidateRuntime> previous = runtimeRepositoryDispatcher.findRuntimeAs(
                input.getUnitCode(), TrackingType.STATUS, StatusTrackingRuntime.class)
                .map(StatusTrackingRuntime::getCandidates)
                .orElse(null);
        Map<String, StatusCandidateRuntime> updated = new LinkedHashMap<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            String coilNo = trimInvisible(PointReader.stringValue(input.getLatestSnapshot(),
                    pointPath(tracking, group.getCoilNo())));
            String colorNo = trimInvisible(PointReader.stringValue(input.getLatestSnapshot(),
                    pointPath(tracking, group.getColorNo())));
            BigDecimal length = decimalValue(input.getLatestSnapshot(),
                    pointPath(tracking, group.getRemainingLength()));
            StatusCandidateRuntime old = previous == null ? null : previous.get(group.getCode());
            boolean coilNoValid = coilNo != null && !coilNo.isEmpty();
            boolean dataComplete = coilNoValid && length != null;
            boolean sameCoil = coilNoValid && old != null && coilNo.equals(old.getCoilNo());
            Integer productNo = !coilNoValid
                    ? null
                    : sameCoil ? old.getProductNo() : queryProductNo(input, group, coilNo);
            CoilerMethodValue coilerMethod = sameCoil && coilerMethodPresent(old)
                    ? coilerMethod(old)
                    : resolveCoilerMethod(input.getLatestSnapshot(), tracking, group);
            List<BigDecimal> lengths = new ArrayList<>();
            BigDecimal maxLength = null;
            if (sameCoil) {
                if (old.getLengths() != null) {
                    lengths.addAll(old.getLengths());
                }
                maxLength = old.getMaxLength();
            }
            if (length != null && coilNoValid) {
                maxLength = sameCoil ? maxLength(old, length) : length;
                lengths.add(length);
            }
            while (lengths.size() > tracking.getSampleCount()) {
                lengths.remove(0);
            }
            StatusCandidateRuntime candidate = StatusCandidateRuntime.builder()
                    .dataComplete(dataComplete)
                    .coilNo(coilNo)
                    .productNo(productNo)
                    .colorNo(colorNo)
                    .coilerMethod(coilerMethod == null ? null : coilerMethod.getCode())
                    .coilerMethodName(coilerMethod == null ? null : coilerMethod.getName())
                    .maxLength(maxLength)
                    .lengths(lengths)
                    .build();
            if (coilNoValid && !sameCoil) {
                StatusResult result = StatusResult.from(candidate, group, input.getUnitCode(),
                        generatedAt, receivedAt(input));
                if (result != null && !blank(result.getCoilerMethod())
                        && !blank(result.getCoilerMethodName())) {
                    results.add(result);
                }
            }
            updated.put(group.getCode(), candidate);
            trackingStepLogger.log(input, "设备窗口更新", group.getCode(), TrackingStepLogger.details(
                    "side", group.getSide(),
                    "coilNo", coilNo,
                    "productNo", productNo,
                    "colorNo", colorNo,
                    "coilerMethod", candidate.getCoilerMethod(),
                    "coilerMethodName", candidate.getCoilerMethodName(),
                    "maxLength", maxLength,
                    "lengths", lengths,
                    "dataComplete", dataComplete,
                    "reason", dataComplete ? null : "当前帧卷号或剩余长度无效"));
        }
        return updated;
    }

    private CoilerMethodValue resolveCoilerMethod(PointSnapshot snapshot,
                                                  StatusTrackingSection tracking,
                                                  StatusPointGroup group) {
        if (tracking.getCoilerMethodDef() == null || group.getCoilerMethod() == null) {
            return null;
        }
        CoilerMethodDefinition definition = tracking.getCoilerMethodDef().definition(group.getSide());
        if (definition == null || definition.getName() == null || definition.getCode() == null
                || definition.getName().size() < 2 || definition.getCode().size() < 2) {
            throw new IllegalArgumentException("开卷卷取方式定义无效: " + group.getCode());
        }
        Boolean selector = selectorValue(snapshot, tracking, group.getCoilerMethod());
        int index = coilerMethodIndex(selector, group.getCoilerMethod());
        return new CoilerMethodValue(
                definition.getCode().get(index), definition.getName().get(index));
    }

    private CoilerMethodValue coilerMethod(StatusCandidateRuntime candidate) {
        if (candidate == null) {
            return null;
        }
        return new CoilerMethodValue(candidate.getCoilerMethod(), candidate.getCoilerMethodName());
    }

    private boolean coilerMethodPresent(StatusCandidateRuntime candidate) {
        return candidate != null && !blank(candidate.getCoilerMethod())
                && !blank(candidate.getCoilerMethodName());
    }

    private Boolean selectorValue(PointSnapshot snapshot,
                                  StatusTrackingSection tracking,
                                  CoilerMethodConfig config) {
        Boolean pointValue = null;
        if (config.getName() != null && !config.getName().trim().isEmpty()
                && config.getType() != null) {
            Object raw = PointReader.rawValue(snapshot,
                    PointReader.pathResolve(tracking.getPointPrefix(), config.getName()));
            pointValue = strictBoolean(raw);
        }
        return pointValue == null ? config.getDefaultValue() : pointValue;
    }

    private Boolean strictBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private Integer queryProductNo(TrackingInput input, StatusPointGroup group, String coilNo) {
        try {
            Integer productNo = qualityRepository.queryProductNo(coilNo);
            trackingStepLogger.log(input, "重复生产次数查询", group.getCode(), TrackingStepLogger.details(
                    "coilNo", coilNo,
                    "productNo", productNo));
            return productNo;
        } catch (RuntimeException e) {
            log.warn("查询钢卷重复生产次数失败，机组编码={}，设备编码={}，钢卷号={}",
                    input.getUnitCode(), group.getCode(), coilNo, e);
            trackingStepLogger.log(input, "重复生产次数查询", group.getCode(), TrackingStepLogger.details(
                    "coilNo", coilNo,
                    "productNo", null,
                    "reason", "查询失败"));
            return null;
        }
    }

    private BigDecimal maxLength(StatusCandidateRuntime old, BigDecimal length) {
        BigDecimal previousMax = old.getMaxLength();
        if (previousMax == null && old.getLengths() != null) {
            previousMax = old.getLengths().stream()
                    .filter(item -> item != null)
                    .max(BigDecimal::compareTo)
                    .orElse(null);
        }
        return previousMax != null && previousMax.compareTo(length) > 0 ? previousMax : length;
    }

    private Map<DeviceSide, SelectedCandidate> selectCandidates(TrackingInput input,
                                                                 StatusTrackingSection tracking,
                                                                 Map<String, StatusCandidateRuntime> candidates) {
        Map<DeviceSide, SelectedCandidate> selected = new LinkedHashMap<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            StatusCandidateRuntime runtime = candidates.get(group.getCode());
            if (runtime == null || Boolean.FALSE.equals(runtime.getDataComplete())
                    || runtime.getLengths() == null
                    || runtime.getLengths().size() < tracking.getSampleCount()) {
                continue;
            }
            List<BigDecimal> lengths = runtime.getLengths();
            BigDecimal first = lengths.get(0);
            BigDecimal latest = lengths.get(lengths.size() - 1);
            BigDecimal signedChange = latest.subtract(first);
            BigDecimal min = lengths.stream().min(BigDecimal::compareTo).orElse(first);
            BigDecimal max = lengths.stream().max(BigDecimal::compareTo).orElse(first);
            BigDecimal absoluteChange = max.subtract(min);
            boolean monotonicityCheckEnabled = Boolean.TRUE.equals(tracking.getMonotonicityCheckEnabled());
            boolean directionMatched = group.getSide() == DeviceSide.UNCOILER
                    ? signedChange.signum() < 0
                    : signedChange.signum() > 0;
            boolean thresholdMatched = absoluteChange.compareTo(tracking.getMinLengthChange()) >= 0;
            trackingStepLogger.log(input, "设备趋势评估", group.getCode(), TrackingStepLogger.details(
                    "side", group.getSide(),
                    "firstLength", first,
                    "latestLength", latest,
                    "change", signedChange,
                    "minLength", min,
                    "maxLength", max,
                    "range", absoluteChange,
                    "monotonicityCheckEnabled", monotonicityCheckEnabled,
                    "directionMatched", directionMatched,
                    "thresholdMatched", thresholdMatched));
            if ((monotonicityCheckEnabled && !directionMatched) || !thresholdMatched) {
                continue;
            }
            SelectedCandidate existing = selected.get(group.getSide());
            if (existing == null || absoluteChange.compareTo(existing.getAbsoluteChange()) > 0) {
                selected.put(group.getSide(), new SelectedCandidate(
                        group, runtime.getCoilNo(), runtime.getProductNo(), runtime.getColorNo(),
                        runtime.getCoilerMethod(), runtime.getCoilerMethodName(), latest,
                        runtime.getMaxLength(), absoluteChange));
            }
        }
        return selected;
    }

    private Map<DeviceSide, StatusCurrentRuntime> current(
            TrackingInput input,
            Map<DeviceSide, SelectedCandidate> selected) {
        Map<DeviceSide, StatusCurrentRuntime> current = new LinkedHashMap<>();
        for (DeviceSide side : RESULT_ORDER) {
            SelectedCandidate candidate = selected.get(side);
            StatusCurrentRuntime runtime = StatusCurrentRuntime.builder()
                    .side(side)
                    .running(candidate != null)
                    .deviceCode(candidate == null ? null : candidate.getGroup().getCode())
                    .deviceName(candidate == null ? null : candidate.getGroup().getName())
                    .coilerMethod(candidate == null ? null : candidate.getCoilerMethod())
                    .coilerMethodName(candidate == null ? null : candidate.getCoilerMethodName())
                    .coilNo(candidate == null ? null : candidate.getCoilNo())
                    .productNo(candidate == null ? null : candidate.getProductNo())
                    .colorNo(candidate == null ? null : candidate.getColorNo())
                    .remainingLength(candidate == null ? null : candidate.getRemainingLength())
                    .maxLength(candidate == null ? null : candidate.getMaxLength())
                    .build();
            current.put(side, runtime);
            trackingStepLogger.log(input, "设备端状态生成", side.getCode(), TrackingStepLogger.details(
                    "running", runtime.getRunning(),
                    "deviceCode", runtime.getDeviceCode(),
                    "coilerMethod", runtime.getCoilerMethod(),
                    "coilerMethodName", runtime.getCoilerMethodName(),
                    "coilNo", runtime.getCoilNo(),
                    "productNo", runtime.getProductNo(),
                    "colorNo", runtime.getColorNo(),
                    "remainingLength", runtime.getRemainingLength(),
                    "maxLength", runtime.getMaxLength()));
        }
        return current;
    }

    private int coilerMethodIndex(Boolean selector, CoilerMethodConfig config) {
        int falseIndex = config.getFalseIndex() == null ? 0 : config.getFalseIndex();
        return Boolean.FALSE.equals(selector) ? falseIndex : 1 - falseIndex;
    }

    private BigDecimal startConditionValue(PointSnapshot snapshot, StatusTrackingSection tracking) {
        try {
            return PointReader.decimalValue(snapshot,
                    pointPath(tracking, tracking.getStartCondition().getPoint()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal decimalValue(PointSnapshot snapshot, String path) {
        try {
            return PointReader.decimalValue(snapshot, path);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean started(BigDecimal actual, StartCondition condition) {
        return actual != null && condition != null && condition.getThreshold() != null
                && actual.compareTo(condition.getThreshold()) >= 0;
    }

    private String pointPath(StatusTrackingSection tracking, PointConfig point) {
        return PointReader.pathResolve(tracking.getPointPrefix(), point == null ? null : point.getName());
    }

    private String trimInvisible(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && invisible(value.charAt(start))) {
            start++;
        }
        while (end > start && invisible(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean invisible(char value) {
        return Character.isWhitespace(value)
                || Character.isSpaceChar(value)
                || Character.isISOControl(value)
                || Character.getType(value) == Character.FORMAT;
    }

    private Instant receivedAt(TrackingInput input) {
        return input.getLatestSnapshot() == null
                || input.getLatestSnapshot().getReceivedAt() == null
                ? Instant.now() : input.getLatestSnapshot().getReceivedAt();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void validateInput(TrackingInput input) {
        if (input == null || input.getTrackingType() != TrackingType.STATUS) {
            throw new IllegalArgumentException("状态跟踪输入和跟踪类型不能为空");
        }
    }

    private void validateConfig(StatusTrackingSection tracking) {
        if (tracking == null || tracking.getStartCondition() == null
                || tracking.getStartCondition().getPoint() == null
                || tracking.getStartCondition().getThreshold() == null
                || tracking.getSampleCount() == null || tracking.getSampleCount() < 2
                || tracking.getMinLengthChange() == null || tracking.getMinLengthChange().signum() < 0
                || tracking.getPoints() == null) {
            throw new IllegalArgumentException("状态跟踪配置无效");
        }
    }

    @Getter
    @AllArgsConstructor
    private static class SelectedCandidate {
        private final StatusPointGroup group;
        private final String coilNo;
        private final Integer productNo;
        private final String colorNo;
        private final String coilerMethod;
        private final String coilerMethodName;
        private final BigDecimal remainingLength;
        private final BigDecimal maxLength;
        private final BigDecimal absoluteChange;
    }

    @Getter
    @AllArgsConstructor
    private static class CoilerMethodValue {
        private final String code;
        private final String name;
    }
}
