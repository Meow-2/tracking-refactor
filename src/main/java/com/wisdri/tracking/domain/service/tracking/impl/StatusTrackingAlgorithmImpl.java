package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.DevicePosition;
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
import com.wisdri.tracking.domain.repository.product.RepeatProdNoRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 根据连续多帧剩余长度变化识别当前运行的开卷机和卷取机。
 */
@Component
@Slf4j
public class StatusTrackingAlgorithmImpl implements TrackingAlgorithm<StatusResult> {
    private static final List<DeviceSide> RESULT_ORDER = Arrays.asList(
            DeviceSide.UNCOILER, DeviceSide.COILER);

    /** 现场点位可能上送的“请求颜色号”占位前缀，不应作为真实钢卷号参与跟踪。 */
    private static final Pattern INVALID_COIL_NO_PREFIX =
            Pattern.compile("^request\\s+color\\b", Pattern.CASE_INSENSITIVE);

    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    @Resource
    private TrackingStepLogger trackingStepLogger;

    @Resource
    private RepeatProdNoRepository repeatProdNoRepository;

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
        StatusTrackingRuntime previousRuntime = runtimeRepositoryDispatcher.findRuntimeAs(
                input.getUnitCode(), TrackingType.STATUS, StatusTrackingRuntime.class)
                .orElse(null);
        RollingState rollingState = rollingState(input, tracking, previousRuntime);
        // 位置模式必须先有有效道次、方向及两端设备；缺任一项时不能沿用上一帧的设备。
        boolean positionMode = positionMode(tracking);
        boolean rollingInputsValid = !positionMode || validPositionSelection(rollingState);
        boolean configuredPairPresent = !positionMode || !rollingInputsValid
                || configuredPairPresent(tracking, rollingState);
        boolean validRollingSelection = rollingInputsValid && configuredPairPresent;
        if (positionMode && !validRollingSelection) {
            trackingStepLogger.log(input, "轧制设备选择无效", TrackingStepLogger.details(
                    "passNo", rollingState.getPassNo(),
                    "rollingDirection", rollingState.getDirection(),
                    "effectiveDirection", rollingState.getDirection() == null ? null
                            : rollingState.getDirection() ^ rollingState.isReverse(),
                    "uncoilerConfigured", !rollingInputsValid ? null
                            : configuredSidePresent(tracking, rollingState, DeviceSide.UNCOILER),
                    "coilerConfigured", !rollingInputsValid ? null
                            : configuredSidePresent(tracking, rollingState, DeviceSide.COILER),
                    "reason", !rollingInputsValid ? "道次或方向无效" : "该方向缺少开卷或卷取设备配置"));
        }
        BigDecimal startValue = startConditionValue(input.getLatestSnapshot(), tracking);
        boolean started = started(startValue, tracking.getStartCondition());
        trackingStepLogger.log(input, "启动条件检查", TrackingStepLogger.details(
                "actual", startValue,
                "threshold", tracking.getStartCondition().getThreshold(),
                "passed", started));

        Instant generatedAt = Instant.now();
        List<StatusResult> results = new ArrayList<>();
        Map<String, StatusCandidateRuntime> candidates = started
                ? updateCandidates(input, tracking, previousRuntime, rollingState, generatedAt, results)
                : new LinkedHashMap<>();
        Map<DeviceSide, SelectedCandidate> selected = started
                ? selectCandidates(input, tracking, candidates, rollingState)
                : new LinkedHashMap<>();
        // 配置存在但尚未形成有效长度趋势时，分别记录哪一侧未选中，便于区分配置缺失。
        if (positionMode && started && validRollingSelection) {
            for (DeviceSide side : RESULT_ORDER) {
                if (!selected.containsKey(side)) {
                    trackingStepLogger.log(input, "轧制设备未选中", side.getCode(),
                            TrackingStepLogger.details(
                                    "passNo", rollingState.getPassNo(),
                                    "effectiveDirection", rollingState.getDirection() ^ rollingState.isReverse(),
                                    "reason", "设备点位或长度趋势尚未满足条件"));
                }
            }
        }
        Map<DeviceSide, StatusCurrentRuntime> current = current(input, selected, previousRuntime,
                tracking.getCurrentClearThreshold(), !started || rollingState.isWindowReset()
                        || !validRollingSelection);
        runtimeRepositoryDispatcher.saveRuntime(StatusTrackingRuntime.builder()
                .unitCode(input.getUnitCode())
                .trackingType(TrackingType.STATUS)
                .receivedAt(input.getLatestSnapshot() == null
                        ? null : input.getLatestSnapshot().getReceivedAt())
                .startConditionPointValue(startValue)
                .rollingDirection(rollingState.getDirection())
                .rollingDirectReverse(tracking.getRolling() == null ? null : rollingState.isReverse())
                .passNo(rollingState.getPassNo())
                .candidates(candidates)
                .current(current)
                .build());
        trackingStepLogger.log(input, "计算完成", TrackingStepLogger.details(
                "started", started,
                "rollingDirection", rollingState.getDirection(),
                "passNo", rollingState.getPassNo(),
                "uncoiler", current.get(DeviceSide.UNCOILER),
                "coiler", current.get(DeviceSide.COILER),
                "currentCount", current.size(),
                "resultCount", results.size()));
        return results;
    }

    private Map<String, StatusCandidateRuntime> updateCandidates(
            TrackingInput input,
            StatusTrackingSection tracking,
            StatusTrackingRuntime previousRuntime,
            RollingState rollingState,
            Instant generatedAt,
            List<StatusResult> results) {
        Map<String, StatusCandidateRuntime> previous = previousRuntime == null
                ? null : previousRuntime.getCandidates();
        // 同一帧先为开卷设备分配次数，避免配置中的卷取设备先读到上一生产次数。
        Map<String, Integer> porRepeatProdNos = allocatePorRepeatProdNos(input, tracking, previous);
        Map<String, StatusCandidateRuntime> updated = new LinkedHashMap<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            DeviceSide side = actualSide(group, tracking, rollingState);
            String coilNo = normalizeCoilNo(PointReader.stringValue(input.getLatestSnapshot(),
                    pointPath(tracking, group.getCoilNo())));
            String colorNo = trimInvisible(PointReader.stringValue(input.getLatestSnapshot(),
                    pointPath(tracking, group.getColorNo())));
            BigDecimal length = decimalValue(input.getLatestSnapshot(),
                    pointPath(tracking, group.getRemainingLength()));
            StatusCandidateRuntime old = previous == null ? null : previous.get(group.getCode());
            boolean coilNoValid = coilNo != null && !coilNo.isEmpty();
            boolean dataComplete = coilNoValid && length != null;
            boolean sameCoil = coilNoValid && old != null && coilNo.equals(old.getCoilNo());
            Integer repeatProdNo = !coilNoValid
                    ? null
                    : sameCoil ? old.getRepeatProdNo()
                    : porDevice(group) ? porRepeatProdNos.get(group.getCode())
                    : queryRepeatProdNo(input, group, coilNo, false);
            // 本道次不参与的设备不生成方式及开卷卷取结果；换道或换向后按新侧别重新取方式。
            CoilerMethodValue coilerMethod = side == null ? null : sameCoil && !rollingState.isWindowReset()
                    && coilerMethodPresent(old)
                    ? coilerMethod(old)
                    : resolveCoilerMethod(input.getLatestSnapshot(), tracking, group, side);
            List<BigDecimal> lengths = new ArrayList<>();
            BigDecimal maxLength = null;
            if (sameCoil && !rollingState.isWindowReset()) {
                if (old.getLengths() != null) {
                    lengths.addAll(old.getLengths());
                }
                maxLength = old.getMaxLength();
            }
            if (length != null && coilNoValid) {
                maxLength = sameCoil && !rollingState.isWindowReset()
                        ? maxLength(old, length) : length;
                lengths.add(length);
            }
            while (lengths.size() > tracking.getSampleCount()) {
                lengths.remove(0);
            }
            StatusCandidateRuntime candidate = StatusCandidateRuntime.builder()
                    .deviceCode(group.getCode())
                    .deviceName(group.getName())
                    .dataComplete(dataComplete)
                    .coilNo(coilNo)
                    .repeatProdNo(repeatProdNo)
                    .colorNo(colorNo)
                    .coilerMethod(coilerMethod == null ? null : coilerMethod.getCode())
                    .coilerMethodName(coilerMethod == null ? null : coilerMethod.getName())
                    .maxLength(maxLength)
                    .lengths(lengths)
                    .build();
            if (side != null && coilNoValid && (!sameCoil || rollingState.isWindowReset())) {
                StatusResult result = StatusResult.from(candidate, group, side,
                        rollingState.getPassNo(), input.getUnitCode(), generatedAt, receivedAt(input));
                if (result != null && !blank(result.getCoilerMethod())
                        && !blank(result.getCoilerMethodName())) {
                    results.add(result);
                }
            }
            updated.put(group.getCode(), candidate);
            trackingStepLogger.log(input, "设备窗口更新", group.getCode(), TrackingStepLogger.details(
                    "configuredSide", group.getSide(),
                    "actualSide", side,
                    "passNo", rollingState.getPassNo(),
                    "coilNo", coilNo,
                    "repeatProdNo", repeatProdNo,
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
                                                  StatusPointGroup group,
                                                  DeviceSide side) {
        if (tracking.getCoilerMethodDef() == null || group.getCoilerMethod() == null) {
            return null;
        }
        CoilerMethodDefinition definition = tracking.getCoilerMethodDef().definition(side);
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

    /**
     * 按配置顺序分配本帧所有开卷设备的新卷次数，不改变最终候选与结果的配置顺序。
     */
    private Map<String, Integer> allocatePorRepeatProdNos(TrackingInput input,
                                                        StatusTrackingSection tracking,
                                                        Map<String, StatusCandidateRuntime> previous) {
        Map<String, Integer> allocated = new LinkedHashMap<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            if (!porDevice(group)) {
                continue;
            }
            String coilNo = normalizeCoilNo(PointReader.stringValue(input.getLatestSnapshot(),
                    pointPath(tracking, group.getCoilNo())));
            StatusCandidateRuntime old = previous == null ? null : previous.get(group.getCode());
            if (coilNo != null && (old == null || !coilNo.equals(old.getCoilNo()))) {
                allocated.put(group.getCode(), queryRepeatProdNo(input, group, coilNo, true));
            }
        }
        return allocated;
    }

    /** 设备编码以 por 开头时才分配下一次生产次数；其余设备只读取当前次数。 */
    private boolean porDevice(StatusPointGroup group) {
        return group.getCode() != null && group.getCode().toLowerCase(Locale.ROOT).startsWith("por");
    }

    /** PG 异常不阻断状态计算，本次卷号对应的次数留空。 */
    private Integer queryRepeatProdNo(TrackingInput input, StatusPointGroup group,
                                   String coilNo, boolean increment) {
        String action = increment ? "分配" : "查询";
        try {
            Integer repeatProdNo = increment
                    ? repeatProdNoRepository.allocateNext(input.getUnitCode(), coilNo)
                    : repeatProdNoRepository.findLatest(input.getUnitCode(), coilNo);
            trackingStepLogger.log(input, "重复生产次数" + action, group.getCode(), TrackingStepLogger.details(
                    "coilNo", coilNo,
                    "repeatProdNo", repeatProdNo));
            return repeatProdNo;
        } catch (RuntimeException e) {
            log.warn("{}钢卷重复生产次数失败，机组编码={}，设备编码={}，钢卷号={}",
                    action, input.getUnitCode(), group.getCode(), coilNo, e);
            trackingStepLogger.log(input, "重复生产次数" + action, group.getCode(), TrackingStepLogger.details(
                    "coilNo", coilNo,
                    "repeatProdNo", null,
                    "reason", action + "失败"));
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
                                                                 Map<String, StatusCandidateRuntime> candidates,
                                                                 RollingState rollingState) {
        Map<DeviceSide, SelectedCandidate> selected = new LinkedHashMap<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            DeviceSide side = actualSide(group, tracking, rollingState);
            if (side == null) {
                continue;
            }
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
            boolean directionMatched = side == DeviceSide.UNCOILER
                    ? signedChange.signum() < 0
                    : signedChange.signum() > 0;
            boolean thresholdMatched = absoluteChange.compareTo(tracking.getMinLengthChange()) >= 0;
            trackingStepLogger.log(input, "设备趋势评估", group.getCode(), TrackingStepLogger.details(
                    "configuredSide", group.getSide(),
                    "actualSide", side,
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
            SelectedCandidate existing = selected.get(side);
            if (existing == null || absoluteChange.compareTo(existing.getAbsoluteChange()) > 0) {
                selected.put(side, new SelectedCandidate(
                        group, runtime.getCoilNo(), runtime.getRepeatProdNo(), runtime.getColorNo(),
                        runtime.getCoilerMethod(), runtime.getCoilerMethodName(), latest,
                        runtime.getMaxLength(), absoluteChange));
            }
        }
        return selected;
    }

    private RollingState rollingState(TrackingInput input,
                                      StatusTrackingSection tracking,
                                      StatusTrackingRuntime previous) {
        if (tracking.getRolling() == null) {
            return new RollingState(null, null, false, false, false);
        }
        Object rawDirection = PointReader.rawValue(input.getLatestSnapshot(),
                pointPath(tracking, tracking.getRolling().getDirectPoint()));
        Boolean currentDirection = strictBoolean(rawDirection);
        Boolean previousDirection = previous == null ? null : previous.getRollingDirection();
        Boolean direction = currentDirection == null ? previousDirection : currentDirection;

        Integer currentPassNo = integerValue(input.getLatestSnapshot(),
                pointPath(tracking, tracking.getRolling().getPassNoPoint()));
        Integer previousPassNo = previous == null ? null : previous.getPassNo();
        Integer passNo = currentPassNo == null ? previousPassNo : currentPassNo;

        boolean hasPreviousCandidates = previous != null && previous.getCandidates() != null
                && !previous.getCandidates().isEmpty();
        boolean reverse = Boolean.TRUE.equals(tracking.getRolling().getDirectReverse());
        boolean currentSideReversed = direction != null && direction ^ reverse;
        boolean previousSideReversed = previousDirection != null && previousDirection ^ reverse;
        // 配置热更新可能只改变 direct_reverse，方向点本身不变也必须重置旧采样窗口。
        boolean reverseChanged = hasPreviousCandidates && previous.getRollingDirectReverse() != null
                && previous.getRollingDirectReverse() != reverse;
        boolean directionChanged = (hasPreviousCandidates && direction != null
                && currentSideReversed != previousSideReversed) || reverseChanged;
        boolean passChanged = hasPreviousCandidates && currentPassNo != null
                && !currentPassNo.equals(previousPassNo);
        boolean windowReset = directionChanged || passChanged;
        trackingStepLogger.log(input, "轧制状态判断", TrackingStepLogger.details(
                "direct", currentDirection,
                "fallbackDirect", currentDirection == null ? previousDirection : null,
                "directReverse", reverse,
                "sideReversed", currentSideReversed,
                "passNo", passNo,
                "directionChanged", directionChanged,
                "directReverseChanged", reverseChanged,
                "passChanged", passChanged,
                "windowReset", windowReset));
        return new RollingState(direction, passNo, reverse, passChanged, windowReset);
    }

    /**
     * 返回设备在本道次的实际侧别；null 表示设备不参与当前轧制。
     * <p>位置模式若缺少完整设备对，全部跳过，避免只发布单侧状态；其他机组沿用原侧别交换。</p>
     */
    private DeviceSide actualSide(StatusPointGroup group,
                                  StatusTrackingSection tracking,
                                  RollingState rollingState) {
        if (positionMode(tracking)) {
            if (!validPositionSelection(rollingState) || !configuredPairPresent(tracking, rollingState)) {
                return null;
            }
            return positionSide(group, rollingState);
        }
        DeviceSide configuredSide = group.getSide();
        if (configuredSide == null || rollingState.getDirection() == null
                || !(rollingState.getDirection() ^ rollingState.isReverse())) {
            return configuredSide;
        }
        return configuredSide == DeviceSide.UNCOILER
                ? DeviceSide.COILER : DeviceSide.UNCOILER;
    }

    /** 只要配置了位置，就按物理位置选设备；转换配置时会校验所有设备的位置。 */
    private boolean positionMode(StatusTrackingSection tracking) {
        return tracking.getPoints().stream().anyMatch(group -> group.getPosition() != null);
    }

    /** 没有有效道次或方向时无法确定物料源端，不参与位置选设备。 */
    private boolean validPositionSelection(RollingState rollingState) {
        Integer passNo = rollingState.getPassNo();
        Boolean direction = rollingState.getDirection();
        return passNo != null && passNo > 0 && direction != null;
    }

    /**
     * 按道次、实际方向和物理位置确定设备职责。
     * <p>首道次源端只能选 por、目标端只能选 tr；后续道次两端都选 tr。
     * 实际方向为 direct_point XOR direct_reverse，false 时从右往左，true 时从左往右。</p>
     */
    private DeviceSide positionSide(StatusPointGroup group, RollingState rollingState) {
        DevicePosition source = rollingState.getDirection() ^ rollingState.isReverse()
                ? DevicePosition.LEFT : DevicePosition.RIGHT;
        boolean sourceDevice = group.getPosition() == source;
        String code = group.getCode().toLowerCase(Locale.ROOT);
        if (rollingState.getPassNo() == 1) {
            if (sourceDevice && code.startsWith("por")) {
                return DeviceSide.UNCOILER;
            }
            return !sourceDevice && code.startsWith("tr") ? DeviceSide.COILER : null;
        }
        return code.startsWith("tr")
                ? sourceDevice ? DeviceSide.UNCOILER : DeviceSide.COILER : null;
    }

    /** 两端都有符合本道次类别和物理位置的设备配置时才允许形成当前设备对。 */
    private boolean configuredPairPresent(StatusTrackingSection tracking, RollingState rollingState) {
        return configuredSidePresent(tracking, rollingState, DeviceSide.UNCOILER)
                && configuredSidePresent(tracking, rollingState, DeviceSide.COILER);
    }

    /** 检查指定侧别在当前方向是否有可参与的设备，不依赖设备实时点位值。 */
    private boolean configuredSidePresent(StatusTrackingSection tracking,
                                          RollingState rollingState,
                                          DeviceSide expectedSide) {
        for (StatusPointGroup group : tracking.getPoints()) {
            if (positionSide(group, rollingState) == expectedSide) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按设备端更新当前状态。候选命中时立即替换，未命中时在阈值内保留上次状态；
     * 停机、换向和换道会跳过保留期并立即清空。
     */
    private Map<DeviceSide, StatusCurrentRuntime> current(
            TrackingInput input,
            Map<DeviceSide, SelectedCandidate> selected,
            StatusTrackingRuntime previousRuntime,
            Integer clearThreshold,
            boolean forceClear) {
        Map<DeviceSide, StatusCurrentRuntime> current = new LinkedHashMap<>();
        for (DeviceSide side : RESULT_ORDER) {
            SelectedCandidate candidate = selected.get(side);
            StatusCurrentRuntime previous = previousRuntime == null || previousRuntime.getCurrent() == null
                    ? null : previousRuntime.getCurrent().get(side);
            StatusCurrentRuntime runtime = forceClear
                    ? emptyCurrent(side, 1)
                    : candidate != null ? selectedCurrent(side, candidate)
                    : retainedOrEmptyCurrent(side, previous, clearThreshold);
            current.put(side, runtime);
            trackingStepLogger.log(input, "设备端状态生成", side.getCode(), TrackingStepLogger.details(
                    "running", runtime.getRunning(),
                    "nullCount", runtime.getNullCount(),
                    "deviceCode", runtime.getDeviceCode(),
                    "coilerMethod", runtime.getCoilerMethod(),
                    "coilerMethodName", runtime.getCoilerMethodName(),
                    "coilNo", runtime.getCoilNo(),
                    "repeatProdNo", runtime.getRepeatProdNo(),
                    "colorNo", runtime.getColorNo(),
                    "remainingLength", runtime.getRemainingLength(),
                    "maxLength", runtime.getMaxLength()));
        }
        return current;
    }

    private StatusCurrentRuntime selectedCurrent(DeviceSide side, SelectedCandidate candidate) {
        return StatusCurrentRuntime.builder()
                .side(side)
                .running(true)
                .nullCount(1)
                .deviceCode(candidate.getGroup().getCode())
                .deviceName(candidate.getGroup().getName())
                .coilerMethod(candidate.getCoilerMethod())
                .coilerMethodName(candidate.getCoilerMethodName())
                .coilNo(candidate.getCoilNo())
                .repeatProdNo(candidate.getRepeatProdNo())
                .colorNo(candidate.getColorNo())
                .remainingLength(candidate.getRemainingLength())
                .maxLength(candidate.getMaxLength())
                .build();
    }

    private StatusCurrentRuntime retainedOrEmptyCurrent(DeviceSide side,
                                                        StatusCurrentRuntime previous,
                                                        Integer clearThreshold) {
        if (previous == null) {
            return emptyCurrent(side, 1);
        }
        int nullCount = nextNullCount(previous.getNullCount());
        if (nullCount > clearThreshold) {
            return emptyCurrent(side, 1);
        }
        return StatusCurrentRuntime.builder()
                .side(side)
                .running(previous.getRunning())
                .nullCount(nullCount)
                .deviceCode(previous.getDeviceCode())
                .deviceName(previous.getDeviceName())
                .coilerMethod(previous.getCoilerMethod())
                .coilerMethodName(previous.getCoilerMethodName())
                .coilNo(previous.getCoilNo())
                .repeatProdNo(previous.getRepeatProdNo())
                .colorNo(previous.getColorNo())
                .remainingLength(previous.getRemainingLength())
                .maxLength(previous.getMaxLength())
                .build();
    }

    private StatusCurrentRuntime emptyCurrent(DeviceSide side, int nullCount) {
        return StatusCurrentRuntime.builder()
                .side(side)
                .running(false)
                .nullCount(nullCount)
                .build();
    }

    private int nextNullCount(Integer previousNullCount) {
        int normalized = previousNullCount == null || previousNullCount < 1 ? 1 : previousNullCount;
        return normalized == Integer.MAX_VALUE ? Integer.MAX_VALUE : normalized + 1;
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

    private Integer integerValue(PointSnapshot snapshot, String path) {
        BigDecimal value = decimalValue(snapshot, path);
        return value == null ? null : value.intValue();
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

    /**
     * 过滤空值、纯英文句点和现场“request color”占位值，保留合法的字母数字混合钢卷号。
     */
    private String normalizeCoilNo(String value) {
        String trimmed = trimInvisible(value);
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        if (INVALID_COIL_NO_PREFIX.matcher(trimmed).find()) {
            return null;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            if (trimmed.charAt(index) != '.') {
                return trimmed;
            }
        }
        return null;
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
                || tracking.getCurrentClearThreshold() == null || tracking.getCurrentClearThreshold() < 1
                || tracking.getPoints() == null
                || tracking.getRolling() != null
                && (tracking.getRolling().getDirectPoint() == null
                || blank(tracking.getRolling().getDirectPoint().getName())
                || tracking.getRolling().getPassNoPoint() == null
                || blank(tracking.getRolling().getPassNoPoint().getName()))) {
            throw new IllegalArgumentException("状态跟踪配置无效");
        }
    }

    @Getter
    @AllArgsConstructor
    private static class RollingState {
        private final Boolean direction;
        private final Integer passNo;
        private final boolean reverse;
        private final boolean passChanged;
        private final boolean windowReset;
    }

    @Getter
    @AllArgsConstructor
    private static class SelectedCandidate {
        private final StatusPointGroup group;
        private final String coilNo;
        private final Integer repeatProdNo;
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
