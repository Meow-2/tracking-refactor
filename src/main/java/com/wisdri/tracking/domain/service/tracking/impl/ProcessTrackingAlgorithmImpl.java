package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.process.LengthMode;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.RollingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.process.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.process.ProcessSegmentRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认过程跟踪算法实现。
 */
@Component
public class ProcessTrackingAlgorithmImpl implements TrackingAlgorithm<ProcessResult> {
    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    /**
     * 异常数据处理服务。
     */
    @Resource
    private AbnormalDataHandlerDispatcher abnormalDataHandlerDispatcher;

    /**
     * 点位事件处理分发器。
     */
    @Resource
    private PointEventHandlerDispatcher pointEventHandlerDispatcher;

    @Resource
    private TrackingStepLogger trackingStepLogger;

    /**
     * 支持过程跟踪。
     */
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.PROCESS == trackingType;
    }

    /**
     * 执行过程跟踪计算。
     */
    @Override
    public List<ProcessResult> calculate(TrackingInput input) {
        long startedAt = System.nanoTime();
        // 先从缓存读取当前配置，后续 pointEvent 处理可能就地更新该配置。
        Optional<ProcessTrackingConfig> configOptional = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(),
                input.getTrackingType(),
                ProcessTrackingConfig.class
        );
        trackingStepLogger.log(input, "计算开始", TrackingStepLogger.details(
                "configPresent", configOptional.isPresent(),
                "lengthMode", configOptional.isPresent() && configOptional.get().getTracking() != null
                        ? configOptional.get().getTracking().getLengthMode() : null
        ));
        if (!configOptional.isPresent()) {
            return complete(input, startedAt, "缺少跟踪配置", null, new ArrayList<>());
        }
        ProcessTrackingConfig config = configOptional.get();

        // 先处理异常点，再处理可能影响配置版本的点位事件。
        abnormalDataHandlerDispatcher.handle(input, config);
        pointEventHandlerDispatcher.handle(input, config);

        // 校验最新快照和启动条件，未达到计算条件时不生成结果。
        PointSnapshot latest = input.getLatestSnapshot();
        if (latest == null || config.getTracking() == null) {
            return complete(input, startedAt, "缺少点位快照或跟踪配置",
                    config.getTracking(), new ArrayList<>());
        }
        TrackingSection tracking = config.getTracking();
        StartCondition condition = tracking.getStartCondition();
        BigDecimal conditionValue = startConditionValue(latest, tracking);
        boolean conditionReached = startConditionReached(conditionValue, condition);
        trackingStepLogger.log(input, "启动条件检查", TrackingStepLogger.details(
                "point", pointName(condition == null ? null : condition.getPoint()),
                "actual", conditionValue,
                "threshold", condition == null ? null : condition.getThreshold(),
                "passed", conditionReached
        ));
        if (!conditionReached) {
            return complete(input, startedAt, "未达到启动条件", tracking, new ArrayList<>());
        }

        // 过滤有效钢卷点位组，后续每个工艺段基于同一组候选点位选择当前钢卷。
        List<TrackingPointGroup> groups = validGroups(latest, tracking);
        trackingStepLogger.log(input, "钢卷分组筛选", TrackingStepLogger.details(
                "groups", groupDetails(latest, tracking, tracking.getPoints(), groups),
                "eligibleCount", groups.size()
        ));
        List<ProcessResult> results = new ArrayList<>();
        if (config.getSegments() == null) {
            return complete(input, startedAt, "segments_missing", tracking, results);
        }

        // 按工艺段逐段选择钢卷并组装跟踪结果。
        for (SegmentConfig segment : config.getSegments()) {
            SelectedGroup selected = selectGroup(input, latest, tracking, segment, groups);
            if (selected == null) {
                continue;
            }
            String coilNo = PointReader.stringValue(latest,
                    trackingPointPath(tracking, selected.group.getCoilNo()));
            BigDecimal speed = PointReader.decimalValue(latest,
                    trackingPointPath(tracking, tracking.getSpeedPoint()));
            Integer passNo = passNo(latest, tracking);
            Map<String, Object> parameters = parameters(latest, segment);
            ProcessResult result = ProcessResult.builder()
                    .unitCode(config.getUnitCode())
                    .trackingType(config.getTrackingType())
                    .segmentCode(segment.getCode())
                    .segmentName(segment.getName())
                    .coilNo(coilNo)
                    .headLength(selected.headLength)
                    .speed(speed)
                    .passNo(passNo)
                    .parameters(parameters)
                    .generatedAt(Instant.now())
                    .receivedAt(latest.getReceivedAt())
                    .build();
            results.add(result);
            trackingStepLogger.log(input, "区段结果生成", segment.getCode(),
                    TrackingStepLogger.details(
                            "coilNo", coilNo,
                            "headLength", selected.headLength,
                            "speed", speed,
                            "passNo", passNo,
                            "parameters", parameters
                    ));
        }
        return complete(input, startedAt, results.isEmpty() ? "未选中任何区段结果" : null,
                tracking, results);
    }

    private List<ProcessResult> complete(TrackingInput input,
                                         long startedAt,
                                         String reason,
                                         TrackingSection tracking,
                                         List<ProcessResult> results) {
        trackingStepLogger.log(input, "计算完成", TrackingStepLogger.details(
                "resultCount", results.size(),
                "reason", reason,
                "elapsedMillis", (System.nanoTime() - startedAt) / 1_000_000L
        ));
        return saveRuntime(input, tracking, results);
    }

    /**
     * 以本轮算法结果整体替换当前过程运行态。
     */
    private List<ProcessResult> saveRuntime(TrackingInput input,
                                            TrackingSection tracking,
                                            List<ProcessResult> results) {
        Map<String, ProcessSegmentRuntime> segments = new LinkedHashMap<>();
        for (ProcessResult result : results) {
            if (result == null || result.getSegmentCode() == null) {
                continue;
            }
            segments.put(result.getSegmentCode(), ProcessSegmentRuntime.builder()
                    .segmentCode(result.getSegmentCode())
                    .coilNo(result.getCoilNo())
                    .headLength(result.getHeadLength())
                    .build());
        }
        runtimeRepositoryDispatcher.saveRuntime(ProcessTrackingRuntime.builder()
                .unitCode(input.getUnitCode())
                .trackingType(input.getTrackingType())
                .updatedAt(Instant.now())
                .speedPointValue(speedPointValue(input.getLatestSnapshot(), tracking))
                .startConditionPointValue(startConditionValue(input.getLatestSnapshot(), tracking))
                .segments(segments)
                .build());
        return results;
    }

    /**
     * 判断启动条件是否达到。
     */
    private BigDecimal startConditionValue(PointSnapshot latest, TrackingSection tracking) {
        StartCondition condition = tracking == null ? null : tracking.getStartCondition();
        if (latest == null || condition == null) {
            return null;
        }
        return PointReader.decimalValue(latest, trackingPointPath(tracking, condition.getPoint()));
    }

    private BigDecimal speedPointValue(PointSnapshot latest, TrackingSection tracking) {
        if (latest == null || tracking == null) {
            return null;
        }
        return PointReader.decimalValue(latest, trackingPointPath(tracking, tracking.getSpeedPoint()));
    }

    private boolean startConditionReached(BigDecimal value, StartCondition condition) {
        if (condition == null) {
            return true;
        }
        return value != null && condition.getThreshold() != null
                && value.compareTo(condition.getThreshold()) >= 0;
    }

    /**
     * 过滤钢卷号为空的跟踪点位组。
     */
    private List<TrackingPointGroup> validGroups(PointSnapshot latest, TrackingSection tracking) {
        List<TrackingPointGroup> result = new ArrayList<>();
        if (tracking.getPoints() == null) {
            return result;
        }
        for (TrackingPointGroup group : tracking.getPoints()) {
            String coilNo = PointReader.stringValue(latest, trackingPointPath(tracking, group.getCoilNo()));
            if (coilNo != null && !coilNo.trim().isEmpty()) {
                result.add(group);
            }
        }
        return result;
    }

    private List<Map<String, Object>> groupDetails(PointSnapshot latest,
                                                   TrackingSection tracking,
                                                   List<TrackingPointGroup> groups,
                                                   List<TrackingPointGroup> eligibleGroups) {
        List<Map<String, Object>> details = new ArrayList<>();
        if (groups == null) {
            return details;
        }
        for (int index = 0; index < groups.size(); index++) {
            TrackingPointGroup group = groups.get(index);
            String coilNo = group == null ? null : PointReader.stringValue(
                    latest, trackingPointPath(tracking, group.getCoilNo()));
            boolean eligible = group != null && eligibleGroups.contains(group);
            details.add(TrackingStepLogger.details(
                    "groupIndex", index,
                    "coilNo", coilNo == null ? null : coilNo.trim(),
                    "rollingCoiler", group == null ? null : group.getIsRollingCoiler(),
                    "eligible", eligible,
                    "reason", eligible ? "候选钢卷" : "钢卷号为空或卷取侧不匹配"
            ));
        }
        return details;
    }

    /**
     * 根据长度模式选择当前工艺段对应的钢卷点位组。
     */
    private SelectedGroup selectGroup(TrackingInput input,
                                      PointSnapshot latest,
                                      TrackingSection tracking,
                                      SegmentConfig segment,
                                      List<TrackingPointGroup> groups) {
        LengthMode lengthMode = tracking.getLengthMode();
        if (LengthMode.WELDER == lengthMode) {
            return selectWelder(input, latest, tracking, segment, groups);
        }
        List<TrackingPointGroup> candidates = groups;
        if (LengthMode.ROLLING == lengthMode) {
            candidates = rollingCandidates(input, latest, tracking, groups);
        }
        return selectCoiler(input, latest, tracking, segment, candidates);
    }

    /**
     * 焊缝模式：选择修正后带头长度大于等于 0 且最小的点位组。
     */
    private SelectedGroup selectWelder(TrackingInput input,
                                       PointSnapshot latest,
                                       TrackingSection tracking,
                                       SegmentConfig segment,
                                       List<TrackingPointGroup> groups) {
        SelectedGroup selected = null;
        int index = Optional.ofNullable(segment.getLengthArrayIndex()).orElse(0);
        List<Map<String, Object>> evaluations = new ArrayList<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            TrackingPointGroup group = groups.get(groupIndex);
            String coilNo = PointReader.stringValue(latest, trackingPointPath(tracking, group.getCoilNo()));
            if (group.getLength() == null || group.getLength().size() <= index) {
                evaluations.add(candidateDetail(groupIndex, coilNo, null, null,
                        false, "缺少长度下标"));
                continue;
            }
            BigDecimal rawLength = PointReader.decimalValue(
                    latest, trackingPointPath(tracking, group.getLength().get(index)));
            BigDecimal headLength = rawLength == null ? null
                    : rawLength.add(Optional.ofNullable(segment.getLengthCorrect()).orElse(BigDecimal.ZERO));
            if (headLength == null) {
                evaluations.add(candidateDetail(groupIndex, coilNo, rawLength, null,
                        false, "缺少长度值"));
                continue;
            }
            if (headLength.compareTo(BigDecimal.ZERO) < 0) {
                evaluations.add(candidateDetail(groupIndex, coilNo, rawLength, headLength,
                        false, "修正后长度小于零"));
                continue;
            }
            evaluations.add(candidateDetail(groupIndex, coilNo, rawLength, headLength,
                    true, "候选钢卷"));
            if (selected == null || headLength.compareTo(selected.headLength) < 0) {
                selected = new SelectedGroup(group, headLength);
            }
        }
        logCandidateEvaluation(input, segment, LengthMode.WELDER, index, evaluations, selected, latest, tracking);
        return selected;
    }

    /**
     * 卷取机模式：选择修正后带头长度满足条件且最大的点位组。
     */
    private SelectedGroup selectCoiler(TrackingInput input,
                                       PointSnapshot latest,
                                       TrackingSection tracking,
                                       SegmentConfig segment,
                                       List<TrackingPointGroup> groups) {
        SelectedGroup selected = null;
        List<Map<String, Object>> evaluations = new ArrayList<>();
        BigDecimal lengthCorrect = Optional.ofNullable(segment.getLengthCorrect()).orElse(BigDecimal.ZERO);
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            TrackingPointGroup group = groups.get(groupIndex);
            String coilNo = PointReader.stringValue(latest, trackingPointPath(tracking, group.getCoilNo()));
            if (group.getLength() == null || group.getLength().isEmpty()) {
                evaluations.add(candidateDetail(groupIndex, coilNo, null, null,
                        false, "缺少长度点位"));
                continue;
            }
            BigDecimal rawLength = PointReader.decimalValue(
                    latest, trackingPointPath(tracking, group.getLength().get(0)));
            BigDecimal headLength = rawLength == null ? null : rawLength.add(lengthCorrect);
            if (headLength == null) {
                evaluations.add(candidateDetail(groupIndex, coilNo, rawLength, null,
                        false, "缺少长度值"));
                continue;
            }
            if (headLength.compareTo(lengthCorrect) < 0) {
                evaluations.add(candidateDetail(groupIndex, coilNo, rawLength, headLength,
                        false, "修正后长度低于最小值"));
                continue;
            }
            evaluations.add(candidateDetail(groupIndex, coilNo, rawLength, headLength,
                    true, "候选钢卷"));
            if (selected == null || headLength.compareTo(selected.headLength) > 0) {
                selected = new SelectedGroup(group, headLength);
            }
        }
        logCandidateEvaluation(input, segment, tracking.getLengthMode(), 0,
                evaluations, selected, latest, tracking);
        return selected;
    }

    /**
     * 轧机模式：根据轧制方向筛选入口侧或出口侧点位组。
     */
    private List<TrackingPointGroup> rollingCandidates(TrackingInput input,
                                                       PointSnapshot latest,
                                                       TrackingSection tracking,
                                                       List<TrackingPointGroup> groups) {
        RollingConfig rolling = tracking.getRolling();
        boolean direct = rolling != null && Boolean.TRUE.equals(PointReader.booleanValue(latest, trackingPointPath(tracking, rolling.getDirectPoint())));
        boolean reverse = rolling != null && Boolean.TRUE.equals(rolling.getDirectReverse());
        boolean targetCoiler = direct ^ reverse;
        List<TrackingPointGroup> result = new ArrayList<>();
        for (TrackingPointGroup group : groups) {
            if (Boolean.TRUE.equals(group.getIsRollingCoiler()) == targetCoiler) {
                result.add(group);
            }
        }
        trackingStepLogger.log(input, "轧制方向判断", TrackingStepLogger.details(
                "direct", direct,
                "directReverse", reverse,
                "targetRollingCoiler", targetCoiler,
                "eligibleGroups", groupDetails(latest, tracking, groups, result)
        ));
        return result;
    }

    private void logCandidateEvaluation(TrackingInput input,
                                        SegmentConfig segment,
                                        LengthMode lengthMode,
                                        int lengthIndex,
                                        List<Map<String, Object>> evaluations,
                                        SelectedGroup selected,
                                        PointSnapshot latest,
                                        TrackingSection tracking) {
        String selectedCoil = selected == null ? null : PointReader.stringValue(
                latest, trackingPointPath(tracking, selected.group.getCoilNo()));
        trackingStepLogger.log(input, "区段候选钢卷评估", segment.getCode(),
                TrackingStepLogger.details(
                        "lengthMode", lengthMode,
                        "lengthArrayIndex", lengthIndex,
                        "lengthCorrect", segment.getLengthCorrect(),
                        "candidates", evaluations,
                        "selectedCoilNo", selectedCoil,
                        "selectedHeadLength", selected == null ? null : selected.headLength
                ));
    }

    private Map<String, Object> candidateDetail(int groupIndex,
                                                String coilNo,
                                                BigDecimal rawLength,
                                                BigDecimal correctedLength,
                                                boolean eligible,
                                                String reason) {
        return TrackingStepLogger.details(
                "groupIndex", groupIndex,
                "coilNo", coilNo,
                "rawLength", rawLength,
                "correctedLength", correctedLength,
                "eligible", eligible,
                "reason", reason
        );
    }

    /**
     * 读取工艺段动态参数。
     */
    private Map<String, Object> parameters(PointSnapshot latest, SegmentConfig segment) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (segment.getPoints() == null) {
            return parameters;
        }
        for (PointConfig point : segment.getPoints()) {
            Object value = PointReader.rawValue(latest, segmentPointPath(segment, point));
            if (value != null) {
                parameters.put(pointName(point), value);
            }
        }
        return parameters;
    }

    /**
     * 读取轧机道次号。
     */
    private Integer passNo(PointSnapshot latest, TrackingSection tracking) {
        if (LengthMode.ROLLING != tracking.getLengthMode() || tracking.getRolling() == null) {
            return null;
        }
        BigDecimal passNo = PointReader.decimalValue(latest, trackingPointPath(tracking, tracking.getRolling().getPassNoPoint()));
        return passNo == null ? null : passNo.intValue();
    }

    /**
     * 构造跟踪段完整点位路径。
     */
    private String trackingPointPath(TrackingSection tracking, PointConfig point) {
        return PointReader.pathResolve(tracking == null ? null : tracking.getPointPrefix(), pointName(point));
    }

    /**
     * 构造工艺段完整点位路径。
     */
    private String segmentPointPath(SegmentConfig segment, PointConfig point) {
        return PointReader.pathResolve(segment == null ? null : segment.getPointPrefix(), pointName(point));
    }

    private String pointName(PointConfig point) {
        return point == null ? null : point.getName();
    }

    @Getter
    @AllArgsConstructor
    private static class SelectedGroup {
        /**
         * 被选中的跟踪点位组。
         */
        private final TrackingPointGroup group;

        /**
         * 该点位组计算出的带头长度。
         */
        private final BigDecimal headLength;
    }
}
