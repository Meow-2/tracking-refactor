package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
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
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import com.wisdri.tracking.domain.service.tracking.trace.TrackingStepLogger;
import lombok.AllArgsConstructor;
import lombok.Getter;
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
public class StatusTrackingAlgorithmImpl implements TrackingAlgorithm<StatusResult> {
    private static final List<DeviceSide> RESULT_ORDER = Arrays.asList(
            DeviceSide.UNCOILER, DeviceSide.COILER);

    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    @Resource
    private TrackingStepLogger trackingStepLogger;

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

        Map<String, StatusCandidateRuntime> candidates = started
                ? updateCandidates(input, tracking)
                : new LinkedHashMap<>();
        Map<DeviceSide, SelectedCandidate> selected = started
                ? selectCandidates(input, tracking, candidates)
                : new LinkedHashMap<>();
        Instant generatedAt = Instant.now();
        List<StatusResult> results = results(config, input, selected, generatedAt);
        Map<DeviceSide, StatusCurrentRuntime> current = current(results);

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
                "resultCount", results.size()));
        return results;
    }

    private Map<String, StatusCandidateRuntime> updateCandidates(TrackingInput input,
                                                                  StatusTrackingSection tracking) {
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
            if (coilNo == null || coilNo.isEmpty() || length == null) {
                trackingStepLogger.log(input, "设备窗口更新", group.getCode(), TrackingStepLogger.details(
                        "reason", "卷号或剩余长度无效",
                        "coilNo", coilNo,
                        "remainingLength", length));
                continue;
            }

            StatusCandidateRuntime old = previous == null ? null : previous.get(group.getCode());
            List<BigDecimal> lengths = new ArrayList<>();
            if (old != null && coilNo.equals(old.getCoilNo()) && old.getLengths() != null) {
                lengths.addAll(old.getLengths());
            }
            lengths.add(length);
            while (lengths.size() > tracking.getSampleCount()) {
                lengths.remove(0);
            }
            StatusCandidateRuntime candidate = StatusCandidateRuntime.builder()
                    .coilNo(coilNo)
                    .colorNo(colorNo)
                    .lengths(lengths)
                    .build();
            updated.put(group.getCode(), candidate);
            trackingStepLogger.log(input, "设备窗口更新", group.getCode(), TrackingStepLogger.details(
                    "side", group.getSide(),
                    "coilNo", coilNo,
                    "colorNo", colorNo,
                    "lengths", lengths));
        }
        return updated;
    }

    private Map<DeviceSide, SelectedCandidate> selectCandidates(TrackingInput input,
                                                                 StatusTrackingSection tracking,
                                                                 Map<String, StatusCandidateRuntime> candidates) {
        Map<DeviceSide, SelectedCandidate> selected = new LinkedHashMap<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            StatusCandidateRuntime runtime = candidates.get(group.getCode());
            if (runtime == null || runtime.getLengths() == null
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
                        group, runtime.getCoilNo(), runtime.getColorNo(), latest, absoluteChange));
            }
        }
        return selected;
    }

    private List<StatusResult> results(StatusTrackingConfig config,
                                       TrackingInput input,
                                       Map<DeviceSide, SelectedCandidate> selected,
                                       Instant generatedAt) {
        List<StatusResult> results = new ArrayList<>();
        for (DeviceSide side : RESULT_ORDER) {
            SelectedCandidate candidate = selected.get(side);
            StatusResult result = StatusResult.builder()
                    .unitCode(config.getUnitCode())
                    .trackingType(TrackingType.STATUS)
                    .generatedAt(generatedAt)
                    .receivedAt(input.getLatestSnapshot() == null
                            ? null : input.getLatestSnapshot().getReceivedAt())
                    .side(side)
                    .running(candidate != null)
                    .deviceCode(candidate == null ? null : candidate.getGroup().getCode())
                    .deviceName(candidate == null ? null : candidate.getGroup().getName())
                    .coilNo(candidate == null ? null : candidate.getCoilNo())
                    .colorNo(candidate == null ? null : candidate.getColorNo())
                    .remainingLength(candidate == null ? null : candidate.getRemainingLength())
                    .build();
            results.add(result);
            trackingStepLogger.log(input, "设备端结果生成", side.getCode(), TrackingStepLogger.details(
                    "running", result.getRunning(),
                    "deviceCode", result.getDeviceCode(),
                    "coilNo", result.getCoilNo(),
                    "colorNo", result.getColorNo(),
                    "remainingLength", result.getRemainingLength()));
        }
        return results;
    }

    private Map<DeviceSide, StatusCurrentRuntime> current(List<StatusResult> results) {
        Map<DeviceSide, StatusCurrentRuntime> current = new LinkedHashMap<>();
        for (StatusResult result : results) {
            current.put(result.getSide(), StatusCurrentRuntime.builder()
                    .side(result.getSide())
                    .running(result.getRunning())
                    .deviceCode(result.getDeviceCode())
                    .deviceName(result.getDeviceName())
                    .coilNo(result.getCoilNo())
                    .colorNo(result.getColorNo())
                    .remainingLength(result.getRemainingLength())
                    .build());
        }
        return current;
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
        private final String colorNo;
        private final BigDecimal remainingLength;
        private final BigDecimal absoluteChange;
    }
}
