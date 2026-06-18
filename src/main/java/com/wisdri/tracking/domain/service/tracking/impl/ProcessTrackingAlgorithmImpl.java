package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.process.LengthMode;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.RollingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.config.process.StartCondition;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.process.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataHandler;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
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
    private TrackingConfigRepository configRepository;

    /**
     * 异常数据处理服务。
     */
    @Resource
    private AbnormalDataHandler abnormalDataHandler;

    /**
     * 点位事件处理分发器。
     */
    @Resource
    private PointEventHandlerDispatcher pointEventHandlerDispatcher;

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
        // 先从缓存读取当前配置，后续 pointEvent 处理可能就地更新该配置。
        Optional<ProcessTrackingConfig> configOptional = configRepository.findAs(
                input.getUnitCode(),
                input.getTrackingType(),
                ProcessTrackingConfig.class
        );
        if (!configOptional.isPresent()) {
            return new ArrayList<>();
        }
        ProcessTrackingConfig config = configOptional.get();

        // 先处理异常点，再处理可能影响配置版本的点位事件。
        abnormalDataHandler.handle(input, config);
        pointEventHandlerDispatcher.handle(input, config);

        // 校验最新快照和启动条件，未达到计算条件时不生成结果。
        PointSnapshot latest = input.getLatestSnapshot();
        if (latest == null || config.getTracking() == null || !startConditionReached(latest, config.getTracking())) {
            return new ArrayList<>();
        }

        // 过滤有效钢卷点位组，后续每个工艺段基于同一组候选点位选择当前钢卷。
        List<TrackingPointGroup> groups = validGroups(latest, config.getTracking());
        List<ProcessResult> results = new ArrayList<>();
        if (config.getSegments() == null) {
            return results;
        }

        // 按工艺段逐段选择钢卷并组装跟踪结果。
        for (SegmentConfig segment : config.getSegments()) {
            SelectedGroup selected = selectGroup(latest, config.getTracking(), segment, groups);
            if (selected == null) {
                continue;
            }
            results.add(ProcessResult.builder()
                    .unitCode(config.getUnitCode())
                    .trackingType(config.getTrackingType())
                    .segmentName(segment.getName())
                    .coilNo(PointReader.stringValue(latest, trackingPointPath(config.getTracking(), selected.group.getCoilNo())))
                    .headLength(selected.headLength)
                    .speed(PointReader.decimalValue(latest, trackingPointPath(config.getTracking(), config.getTracking().getSpeedPoint())))
                    .passNo(passNo(latest, config.getTracking()))
                    .parameters(parameters(latest, segment))
                    .generatedAt(Instant.now())
                    .build());
        }
        return results;
    }

    /**
     * 判断启动条件是否达到。
     */
    private boolean startConditionReached(PointSnapshot latest, TrackingSection tracking) {
        StartCondition condition = tracking.getStartCondition();
        if (condition == null) {
            return true;
        }
        BigDecimal value = PointReader.decimalValue(latest, trackingPointPath(tracking, condition.getPoint()));
        return value != null && value.compareTo(condition.getThreshold()) >= 0;
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

    /**
     * 根据长度模式选择当前工艺段对应的钢卷点位组。
     */
    private SelectedGroup selectGroup(PointSnapshot latest,
                                      TrackingSection tracking,
                                      SegmentConfig segment,
                                      List<TrackingPointGroup> groups) {
        LengthMode lengthMode = tracking.getLengthMode();
        if (LengthMode.WELDER == lengthMode) {
            return selectWelder(latest, tracking, segment, groups);
        }
        List<TrackingPointGroup> candidates = groups;
        if (LengthMode.ROLLING == lengthMode) {
            candidates = rollingCandidates(latest, tracking, groups);
        }
        return selectCoiler(latest, tracking, segment, candidates);
    }

    /**
     * 焊缝模式：选择修正后带头长度大于等于 0 且最小的点位组。
     */
    private SelectedGroup selectWelder(PointSnapshot latest,
                                       TrackingSection tracking,
                                       SegmentConfig segment,
                                       List<TrackingPointGroup> groups) {
        SelectedGroup selected = null;
        int index = Optional.ofNullable(segment.getLengthArrayIndex()).orElse(0);
        for (TrackingPointGroup group : groups) {
            if (group.getLength() == null || group.getLength().size() <= index) {
                continue;
            }
            BigDecimal headLength = correctedLength(latest, tracking, group.getLength().get(index), segment);
            if (headLength == null || headLength.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            if (selected == null || headLength.compareTo(selected.headLength) < 0) {
                selected = new SelectedGroup(group, headLength);
            }
        }
        return selected;
    }

    /**
     * 卷取机模式：选择修正后带头长度满足条件且最大的点位组。
     */
    private SelectedGroup selectCoiler(PointSnapshot latest,
                                       TrackingSection tracking,
                                       SegmentConfig segment,
                                       List<TrackingPointGroup> groups) {
        SelectedGroup selected = null;
        for (TrackingPointGroup group : groups) {
            if (group.getLength() == null || group.getLength().isEmpty()) {
                continue;
            }
            BigDecimal headLength = correctedLength(latest, tracking, group.getLength().get(0), segment);
            if (headLength == null || headLength.compareTo(Optional.ofNullable(segment.getLengthCorrect()).orElse(BigDecimal.ZERO)) < 0) {
                continue;
            }
            if (selected == null || headLength.compareTo(selected.headLength) > 0) {
                selected = new SelectedGroup(group, headLength);
            }
        }
        return selected;
    }

    /**
     * 轧机模式：根据轧制方向筛选入口侧或出口侧点位组。
     */
    private List<TrackingPointGroup> rollingCandidates(PointSnapshot latest, TrackingSection tracking, List<TrackingPointGroup> groups) {
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
        return result;
    }

    /**
     * 计算修正后的带头长度。
     */
    private BigDecimal correctedLength(PointSnapshot latest,
                                       TrackingSection tracking,
                                       String lengthPoint,
                                       SegmentConfig segment) {
        BigDecimal length = PointReader.decimalValue(latest, trackingPointPath(tracking, lengthPoint));
        if (length == null) {
            return null;
        }
        return length.add(Optional.ofNullable(segment.getLengthCorrect()).orElse(BigDecimal.ZERO));
    }

    /**
     * 读取工艺段动态参数。
     */
    private Map<String, Object> parameters(PointSnapshot latest, SegmentConfig segment) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (segment.getPoints() == null) {
            return parameters;
        }
        for (String point : segment.getPoints()) {
            Object value = PointReader.rawValue(latest, segmentPointPath(segment, point));
            if (value != null) {
                parameters.put(point, value);
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
    private String trackingPointPath(TrackingSection tracking, String point) {
        return PointReader.pathResolve(tracking == null ? null : tracking.getPointPrefix(), point);
    }

    /**
     * 构造工艺段完整点位路径。
     */
    private String segmentPointPath(SegmentConfig segment, String point) {
        return PointReader.pathResolve(segment == null ? null : segment.getPointPrefix(), point);
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
