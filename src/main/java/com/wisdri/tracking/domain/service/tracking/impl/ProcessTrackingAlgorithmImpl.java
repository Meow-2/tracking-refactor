package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.process.LengthMode;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.process.TrackingSection;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.process.ProcessSegmentRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import com.wisdri.tracking.domain.service.tracking.CellCodeResolver;
import com.wisdri.tracking.domain.service.tracking.status.StatusRepeatProdNoResolver;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
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

        // status 与 process 使用不同消息；换道或换向时不能沿用上一状态帧的设备。
        if (LengthMode.ROLLING == tracking.getLengthMode()
                && !rollingStatusMatches(input.getStatusContext(), latest, tracking)) {
            trackingStepLogger.log(input, "轧制状态不匹配", TrackingStepLogger.details(
                    "processPassNo", passNo(latest, tracking),
                    "statusPassNo", input.getStatusContext() == null ? null : input.getStatusContext().getPassNo(),
                    "processDirection", rollingDirection(latest, tracking),
                    "statusDirection", input.getStatusContext() == null
                            ? null : input.getStatusContext().getRollingDirection(),
                    "statusDirectReverse", input.getStatusContext() == null
                            ? null : input.getStatusContext().getRollingDirectReverse()));
            return complete(input, startedAt, "等待同道次同方向的状态", tracking, new ArrayList<>());
        }

        // 焊缝模式仍通过过程点位选卷；卷取机和轧机模式统一使用固化的状态上下文。
        List<TrackingPointGroup> groups = new ArrayList<>();
        if (LengthMode.WELDER == tracking.getLengthMode()) {
            groups = validGroups(latest, tracking);
            trackingStepLogger.log(input, "钢卷分组筛选", TrackingStepLogger.details(
                    "groups", groupDetails(latest, tracking, tracking.getPoints(), groups),
                    "eligibleCount", groups.size()
            ));
        }
        List<ProcessResult> results = new ArrayList<>();
        if (config.getSegments() == null) {
            return complete(input, startedAt, "segments_missing", tracking, results);
        }

        // 按工艺段逐段选择钢卷并组装跟踪结果。
        for (SegmentConfig segment : config.getSegments()) {
            SelectedMaterial selected = selectMaterial(input, latest, tracking, segment, groups);
            if (selected == null) {
                continue;
            }
            BigDecimal speed = PointReader.decimalValue(latest,
                    trackingPointPath(tracking, tracking.getSpeedPoint()));
            Integer passNo = passNo(latest, tracking);
            String cellCode = cellCode(config.getUnitCode(), latest, segment);
            Map<String, Object> parameters = parameters(latest, segment);
            ProcessResult result = ProcessResult.builder()
                    .unitCode(config.getUnitCode())
                    .trackingType(config.getTrackingType())
                    .segmentCode(segment.getCode())
                    .segmentName(segment.getName())
                    .cellCode(cellCode)
                    .coilNo(selected.coilNo)
                    .repeatProdNo(selected.repeatProdNo)
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
                            "coilNo", selected.coilNo,
                            "repeatProdNo", selected.repeatProdNo,
                            "headLength", selected.headLength,
                            "speed", speed,
                            "passNo", passNo,
                            "cellCode", cellCode,
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

    /** 根据长度模式选择当前工艺段对应的物料。 */
    private SelectedMaterial selectMaterial(TrackingInput input,
                                            PointSnapshot latest,
                                            TrackingSection tracking,
                                            SegmentConfig segment,
                                            List<TrackingPointGroup> groups) {
        LengthMode lengthMode = tracking.getLengthMode();
        if (LengthMode.WELDER == lengthMode) {
            return selectWelder(input, latest, tracking, segment, groups);
        }
        return selectStatusMaterial(input, tracking, segment);
    }

    /**
     * 焊缝模式：选择修正后带头长度大于等于 0 且最小的点位组。
     */
    private SelectedMaterial selectWelder(TrackingInput input,
                                          PointSnapshot latest,
                                          TrackingSection tracking,
                                          SegmentConfig segment,
                                          List<TrackingPointGroup> groups) {
        SelectedMaterial selected = null;
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
                selected = new SelectedMaterial(coilNo,
                        repeatProdNo(input.getStatusContext(), coilNo), headLength);
            }
        }
        logCandidateEvaluation(input, segment, LengthMode.WELDER, index, evaluations, selected);
        return selected;
    }

    /**
     * 卷取机、轧机模式使用状态算法识别出的当前开卷端物料。
     */
    private SelectedMaterial selectStatusMaterial(TrackingInput input,
                                                  TrackingSection tracking,
                                                  SegmentConfig segment) {
        StatusTrackingContext context = input.getStatusContext();
        Map<DeviceSide, StatusCurrentRuntime> current = context == null ? null : context.getCurrent();
        StatusCurrentRuntime coiler = current == null ? null : current.get(DeviceSide.COILER);
        StatusCurrentRuntime uncoiler = current == null ? null : current.get(DeviceSide.UNCOILER);
        BigDecimal coilerRemainingLength = coiler == null ? null : coiler.getRemainingLength();
        BigDecimal lengthCorrect = Optional.ofNullable(segment.getLengthCorrect()).orElse(BigDecimal.ZERO);
        boolean coilerStarted = coilerRemainingLength != null
                && coilerRemainingLength.compareTo(BigDecimal.ZERO) > 0;
        boolean uncoilerComplete = uncoiler != null && !blank(uncoiler.getCoilNo())
                && uncoiler.getMaxLength() != null && uncoiler.getRemainingLength() != null;
        BigDecimal headLength = uncoilerComplete
                ? uncoiler.getMaxLength().subtract(uncoiler.getRemainingLength()).add(lengthCorrect)
                : null;
        boolean eligible = coilerStarted && uncoilerComplete;
        trackingStepLogger.log(input, "状态物料检查", segment.getCode(), TrackingStepLogger.details(
                "lengthMode", tracking.getLengthMode(),
                "coilerRemainingLength", coilerRemainingLength,
                "uncoilerCoilNo", uncoiler == null ? null : uncoiler.getCoilNo(),
                "uncoilerRepeatProdNo", uncoiler == null ? null : uncoiler.getRepeatProdNo(),
                "uncoilerMaxLength", uncoiler == null ? null : uncoiler.getMaxLength(),
                "uncoilerRemainingLength", uncoiler == null ? null : uncoiler.getRemainingLength(),
                "lengthCorrect", lengthCorrect,
                "headLength", headLength,
                "eligible", eligible,
                "reason", eligible ? "当前卷取端已开始卷取" : statusMaterialReason(coilerStarted, uncoiler)
        ));
        return eligible ? new SelectedMaterial(uncoiler.getCoilNo(),
                uncoiler.getRepeatProdNo(), headLength) : null;
    }

    private String statusMaterialReason(boolean coilerStarted, StatusCurrentRuntime uncoiler) {
        if (!coilerStarted) {
            return "卷取端剩余长度未大于零";
        }
        if (uncoiler == null) {
            return "缺少当前开卷端状态";
        }
        if (blank(uncoiler.getCoilNo())) {
            return "当前开卷端钢卷号为空";
        }
        if (uncoiler.getMaxLength() == null) {
            return "当前开卷端最大长度为空";
        }
        return "当前开卷端剩余长度为空";
    }

    /**
     * 按钢卷号从当前设备、候选设备中查找重复生产次数。
     */
    private Integer repeatProdNo(StatusTrackingContext context, String coilNo) {
        return StatusRepeatProdNoResolver.find(context, coilNo);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void logCandidateEvaluation(TrackingInput input,
                                        SegmentConfig segment,
                                        LengthMode lengthMode,
                                        int lengthIndex,
                                        List<Map<String, Object>> evaluations,
                                        SelectedMaterial selected) {
        String selectedCoil = selected == null ? null : selected.coilNo;
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
            // cell_code 是固定结果列；同名原始点位不能覆盖格式化后的加工单元代码。
            if ("cell_code".equalsIgnoreCase(pointName(point))) {
                continue;
            }
            Object value = PointReader.rawValue(latest, segmentPointPath(segment, point));
            if (value != null) {
                parameters.put(pointName(point), value);
            }
        }
        return parameters;
    }

    /**
     * 按本段配置读取加工单元序号，并生成大写机组代码加三位序号的代码。
     * 点位已配置但值缺失、非整数或超出 0～999 时返回 null，不回退到默认序号。
     */
    private String cellCode(String unitCode, PointSnapshot latest, SegmentConfig segment) {
        return CellCodeResolver.resolve(unitCode, latest, segment.getPointPrefix(),
                segment.getCellCodeValue(), segment.getCellCodePoint());
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
     * 校验固化的 status 状态与当前 process 帧是否属于同一道次、同一原始方向及反转配置。
     * <p>status 与 process 分别订阅消息，校验失败时本轮不生成结果，等待匹配的状态帧。</p>
     */
    private boolean rollingStatusMatches(StatusTrackingContext context,
                                         PointSnapshot latest,
                                         TrackingSection tracking) {
        Integer processPassNo = passNo(latest, tracking);
        Boolean processDirection = rollingDirection(latest, tracking);
        boolean reverse = tracking.getRolling() != null
                && Boolean.TRUE.equals(tracking.getRolling().getDirectReverse());
        return context != null && processPassNo != null && processPassNo > 0
                && processDirection != null
                && processPassNo.equals(context.getPassNo())
                && processDirection.equals(context.getRollingDirection())
                && Boolean.valueOf(reverse).equals(context.getRollingDirectReverse());
    }

    /** 将布尔值、0/1 数值及文本方向点统一为原始布尔值；无效值返回 null。 */
    private Boolean rollingDirection(PointSnapshot latest, TrackingSection tracking) {
        if (tracking.getRolling() == null) {
            return null;
        }
        Object raw = PointReader.rawValue(latest,
                trackingPointPath(tracking, tracking.getRolling().getDirectPoint()));
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        return null;
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
    private static class SelectedMaterial {
        /** 当前物料钢卷号。 */
        private final String coilNo;

        /** 当前物料重复生产次数。 */
        private final Integer repeatProdNo;

        /** 当前物料计算出的带头长度。 */
        private final BigDecimal headLength;
    }
}
