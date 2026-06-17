package com.wisdri.tracking.domain.service.process.impl;

import com.wisdri.tracking.domain.model.config.LengthMode;
import com.wisdri.tracking.domain.model.config.RollingConfig;
import com.wisdri.tracking.domain.model.config.SegmentConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.service.process.ProcessTrackingAlgorithm;
import org.springframework.stereotype.Component;

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
public class ProcessTrackingAlgorithmImpl implements ProcessTrackingAlgorithm {

    /**
     * 执行过程跟踪计算。
     */
    @Override
    public List<ProcessResult> calculate(TrackingInput input) {
        TrackingConfig config = input.getConfig();
        PointSnapshot latest = input.getLatestSnapshot();
        if (config == null || latest == null || config.getTracking() == null || !startConditionReached(latest, config.getTracking())) {
            return new ArrayList<>();
        }
        List<TrackingPointGroup> groups = validGroups(latest, config.getTracking());
        List<ProcessResult> results = new ArrayList<>();
        if (config.getSegments() == null) {
            return results;
        }
        for (SegmentConfig segment : config.getSegments()) {
            SelectedGroup selected = selectGroup(latest, config.getTracking(), segment, groups);
            if (selected == null) {
                continue;
            }
            results.add(ProcessResult.builder()
                    .unitCode(config.getUnitCode())
                    .trackingType(config.getTrackingType())
                    .segmentName(segment.getName())
                    .coilNo(stringValue(latest, selected.group.getCoilNoPoint()))
                    .headLength(selected.headLength)
                    .speed(decimalValue(latest, config.getTracking().getSpeedPoint()))
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
        BigDecimal value = decimalValue(latest, condition.getPoint());
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
            String coilNo = stringValue(latest, group.getCoilNoPoint());
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
            return selectWelder(latest, segment, groups);
        }
        List<TrackingPointGroup> candidates = groups;
        if (LengthMode.ROLLING == lengthMode) {
            candidates = rollingCandidates(latest, tracking, groups);
        }
        return selectCoiler(latest, segment, candidates);
    }

    /**
     * 焊缝模式：选择修正后带头长度大于等于 0 且最小的点位组。
     */
    private SelectedGroup selectWelder(PointSnapshot latest, SegmentConfig segment, List<TrackingPointGroup> groups) {
        SelectedGroup selected = null;
        int index = Optional.ofNullable(segment.getLengthArrayIndex()).orElse(0);
        for (TrackingPointGroup group : groups) {
            if (group.getLengthPoints() == null || group.getLengthPoints().size() <= index) {
                continue;
            }
            BigDecimal headLength = correctedLength(latest, group.getLengthPoints().get(index), segment);
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
    private SelectedGroup selectCoiler(PointSnapshot latest, SegmentConfig segment, List<TrackingPointGroup> groups) {
        SelectedGroup selected = null;
        for (TrackingPointGroup group : groups) {
            if (group.getLengthPoints() == null || group.getLengthPoints().isEmpty()) {
                continue;
            }
            BigDecimal headLength = correctedLength(latest, group.getLengthPoints().get(0), segment);
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
        boolean direct = rolling != null && Boolean.TRUE.equals(booleanValue(latest, rolling.getDirectPoint()));
        boolean reverse = rolling != null && Boolean.TRUE.equals(rolling.getDirectReverse());
        boolean targetCoiler = direct ^ reverse;
        List<TrackingPointGroup> result = new ArrayList<>();
        for (TrackingPointGroup group : groups) {
            if (Boolean.TRUE.equals(group.getRollingCoiler()) == targetCoiler) {
                result.add(group);
            }
        }
        return result;
    }

    /**
     * 计算修正后的带头长度。
     */
    private BigDecimal correctedLength(PointSnapshot latest, String lengthPoint, SegmentConfig segment) {
        BigDecimal length = decimalValue(latest, lengthPoint);
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
            latest.value(point).ifPresent(value -> parameters.put(point, value.getRawValue()));
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
        BigDecimal passNo = decimalValue(latest, tracking.getRolling().getPassNoPoint());
        return passNo == null ? null : passNo.intValue();
    }

    /**
     * 按字符串读取点位值。
     */
    private String stringValue(PointSnapshot snapshot, String point) {
        return snapshot.value(point).map(PointValue::stringValue).orElse(null);
    }

    /**
     * 按数字读取点位值。
     */
    private BigDecimal decimalValue(PointSnapshot snapshot, String point) {
        return snapshot.value(point).map(PointValue::decimalValue).orElse(null);
    }

    /**
     * 按布尔值读取点位值。
     */
    private Boolean booleanValue(PointSnapshot snapshot, String point) {
        return snapshot.value(point).map(PointValue::booleanValue).orElse(null);
    }

    private static class SelectedGroup {
        /**
         * 被选中的跟踪点位组。
         */
        private final TrackingPointGroup group;

        /**
         * 该点位组计算出的带头长度。
         */
        private final BigDecimal headLength;

        /**
         * 创建已选点位组结果。
         */
        private SelectedGroup(TrackingPointGroup group, BigDecimal headLength) {
            this.group = group;
            this.headLength = headLength;
        }
    }
}
