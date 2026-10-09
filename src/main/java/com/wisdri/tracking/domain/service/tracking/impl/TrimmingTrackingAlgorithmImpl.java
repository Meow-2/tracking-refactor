package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingLengthMode;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingConfig;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.runtime.trimming.TrimmingSegmentRuntime;
import com.wisdri.tracking.domain.model.runtime.trimming.TrimmingTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.model.tracking.trimming.TrimmingResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import com.wisdri.tracking.domain.service.tracking.status.StatusRepeatProdNoResolver;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import com.wisdri.tracking.domain.service.tracking.CellCodeResolver;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 圆盘剪切边量跟踪算法。
 */
@Component
public class TrimmingTrackingAlgorithmImpl implements TrackingAlgorithm<TrimmingResult> {
    private static final String DISC_SEGMENT = "disc";
    private static final BigDecimal MIN_TRIMMING_DIFFERENCE = BigDecimal.ONE;
    private static final BigDecimal TWO = new BigDecimal("2");

    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    @Resource
    private TrackingStepLogger trackingStepLogger;

    @Resource
    private TrackingProperties trackingProperties;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.TRIMMING == trackingType;
    }

    @Override
    public List<TrimmingResult> calculate(TrackingInput input) {
        if (input == null || input.getLatestSnapshot() == null) {
            return Collections.emptyList();
        }
        Optional<TrimmingTrackingConfig> configOptional = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(), TrackingType.TRIMMING, TrimmingTrackingConfig.class);
        if (!configOptional.isPresent() || configOptional.get().getTracking() == null) {
            return skip(input, "切边配置不存在");
        }
        TrimmingTrackingConfig config = configOptional.get();
        TrimmingTrackingSection tracking = config.getTracking();
        PointSnapshot snapshot = input.getLatestSnapshot();
        BigDecimal conditionValue = startConditionValue(snapshot, tracking);
        if (!startConditionReached(conditionValue, tracking.getStartCondition())) {
            return skip(input, "未达到启动条件");
        }
        SegmentConfig segment = discSegment(config.getSegments());
        if (segment == null || segment.getPoints() == null || segment.getPoints().size() < 2) {
            return skip(input, "disc 段或宽度点位配置不完整");
        }
        Material material = material(input, tracking, segment);
        if (material == null || blank(material.coilNo) || material.repeatProdNo == null) {
            return skip(input, "当前钢卷号或物料重复生产次数不存在");
        }
        BigDecimal widthPv = segmentValue(snapshot, segment, segment.getPoints().get(0));
        BigDecimal widthSv = segmentValue(snapshot, segment, segment.getPoints().get(1));
        if (widthPv == null || widthSv == null) {
            return skip(input, "宽度实际值或设定值不存在");
        }
        BigDecimal trimmingLength = trimmingLength(widthPv, widthSv);
        StatusTrackingContext statusContext = input.getStatusContext();
        String cellCode = CellCodeResolver.resolve(input.getUnitCode(), null, null,
                statusContext == null ? null : statusContext.getPassNo(), null);
        TrimmingSegmentRuntime existing = existing(input.getUnitCode());
        boolean sameMaterial = cellCode != null && existing != null
                && Objects.equals(cellCode, existing.getCellCode())
                && Objects.equals(material.coilNo, existing.getCoilNo())
                && Objects.equals(material.repeatProdNo, existing.getRepeatProdNo());
        if (sameMaterial && existing.getTrimmingLength() != null
                && existing.getTrimmingLength().compareTo(BigDecimal.ZERO) != 0) {
            return skip(input, "当前物料已记录非零切边量");
        }
        if (sameMaterial && trimmingLength.compareTo(BigDecimal.ZERO) == 0) {
            return skip(input, "当前物料已记录零切边量且无需更新");
        }

        TrimmingResult result = TrimmingResult.builder()
                .unitCode(input.getUnitCode())
                .cellCode(cellCode)
                .trackingType(TrackingType.TRIMMING)
                .generatedAt(Instant.now())
                .receivedAt(snapshot.getReceivedAt())
                .segmentCode(segment.getCode())
                .inMatNo(material.coilNo)
                .repeatProdNo(material.repeatProdNo)
                .headLength(material.headLength)
                .coilWidthPv(widthPv)
                .coilWidthSv(widthSv)
                .trimmingLength(trimmingLength)
                .updateExisting(sameMaterial)
                .build();
        trackingStepLogger.log(input, "切边结果生成", segment.getCode(), TrackingStepLogger.details(
                "coilNo", material.coilNo,
                "repeatProdNo", material.repeatProdNo,
                "widthPv", widthPv,
                "widthSv", widthSv,
                "trimmingLength", trimmingLength,
                "updateExisting", sameMaterial));
        return Collections.singletonList(result);
    }

    /** 数据库保存成功后才覆盖运行态，运行态即后续消息的去重依据。 */
    @Override
    public void afterPersist(TrackingInput input, List<TrimmingResult> results) {
        if (!trackingProperties.trimmingStorageEnabled() || results == null || results.isEmpty()) {
            return;
        }
        TrimmingResult result = results.get(results.size() - 1);
        TrimmingTrackingSection tracking = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(), TrackingType.TRIMMING, TrimmingTrackingConfig.class)
                .map(TrimmingTrackingConfig::getTracking).orElse(null);
        TrimmingSegmentRuntime segment = TrimmingSegmentRuntime.builder()
                .segmentCode(result.getSegmentCode())
                .cellCode(result.getCellCode())
                .coilNo(result.getInMatNo())
                .repeatProdNo(result.getRepeatProdNo())
                .headLength(result.getHeadLength())
                .coilWidthPv(result.getCoilWidthPv())
                .coilWidthSv(result.getCoilWidthSv())
                .trimmingLength(result.getTrimmingLength())
                .build();
        runtimeRepositoryDispatcher.saveRuntime(TrimmingTrackingRuntime.builder()
                .unitCode(input.getUnitCode())
                .trackingType(TrackingType.TRIMMING)
                .updatedAt(Instant.now())
                .speedPointValue(speedPointValue(input.getLatestSnapshot(), tracking))
                .startConditionPointValue(startConditionValue(input.getLatestSnapshot(), tracking))
                .segments(Collections.singletonMap(result.getSegmentCode(), segment))
                .build());
    }

    private Material material(TrackingInput input,
                              TrimmingTrackingSection tracking,
                              SegmentConfig segment) {
        if (tracking.getLengthMode() == TrimmingLengthMode.STATUS) {
            return statusMaterial(input.getStatusContext());
        }
        if (tracking.getLengthMode() == TrimmingLengthMode.WELDER) {
            return welderMaterial(input, tracking, segment);
        }
        return null;
    }

    private Material statusMaterial(StatusTrackingContext context) {
        StatusCurrentRuntime current = context == null || context.getCurrent() == null
                ? null : context.getCurrent().get(DeviceSide.UNCOILER);
        if (current == null || !Boolean.TRUE.equals(current.getRunning())) {
            return null;
        }
        return new Material(current.getCoilNo(), current.getRepeatProdNo(), null);
    }

    /** 与 process 的 welder 模式一致，选择修正后最小的非负带头长度。 */
    private Material welderMaterial(TrackingInput input,
                                    TrimmingTrackingSection tracking,
                                    SegmentConfig segment) {
        PointSnapshot snapshot = input.getLatestSnapshot();
        Material selected = null;
        int lengthIndex = Optional.ofNullable(segment.getLengthArrayIndex()).orElse(0);
        BigDecimal correct = Optional.ofNullable(segment.getLengthCorrect()).orElse(BigDecimal.ZERO);
        if (tracking.getPoints() == null) {
            return null;
        }
        for (TrackingPointGroup group : tracking.getPoints()) {
            if (group == null || group.getLength() == null || group.getLength().size() <= lengthIndex) {
                continue;
            }
            String coilNo = PointReader.stringValue(snapshot, trackingPath(tracking, group.getCoilNo()));
            BigDecimal rawLength = PointReader.decimalValue(
                    snapshot, trackingPath(tracking, group.getLength().get(lengthIndex)));
            BigDecimal headLength = rawLength == null ? null : rawLength.add(correct);
            if (blank(coilNo) || headLength == null || headLength.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            Integer repeatProdNo = StatusRepeatProdNoResolver.find(input.getStatusContext(), coilNo);
            if (selected == null || headLength.compareTo(selected.headLength) < 0) {
                selected = new Material(coilNo, repeatProdNo, headLength);
            }
        }
        return selected;
    }

    private BigDecimal trimmingLength(BigDecimal widthPv, BigDecimal widthSv) {
        BigDecimal difference = widthPv.subtract(widthSv);
        return difference.compareTo(MIN_TRIMMING_DIFFERENCE) <= 0
                ? BigDecimal.ZERO : difference.divide(TWO);
    }

    private TrimmingSegmentRuntime existing(String unitCode) {
        return runtimeRepositoryDispatcher.findRuntimeAs(
                        unitCode, TrackingType.TRIMMING, TrimmingTrackingRuntime.class)
                .map(TrimmingTrackingRuntime::getSegments)
                .map(segments -> segments.get(DISC_SEGMENT))
                .orElse(null);
    }

    private SegmentConfig discSegment(List<SegmentConfig> segments) {
        if (segments == null) {
            return null;
        }
        for (SegmentConfig segment : segments) {
            if (segment != null && DISC_SEGMENT.equals(segment.getCode())) {
                return segment;
            }
        }
        return null;
    }

    private BigDecimal startConditionValue(PointSnapshot snapshot, TrimmingTrackingSection tracking) {
        StartCondition condition = tracking == null ? null : tracking.getStartCondition();
        return snapshot == null || condition == null ? null
                : PointReader.decimalValue(snapshot, trackingPath(tracking, condition.getPoint()));
    }

    private BigDecimal speedPointValue(PointSnapshot snapshot, TrimmingTrackingSection tracking) {
        return snapshot == null || tracking == null ? null
                : PointReader.decimalValue(snapshot, trackingPath(tracking, tracking.getSpeedPoint()));
    }

    private boolean startConditionReached(BigDecimal value, StartCondition condition) {
        return condition == null || value != null && condition.getThreshold() != null
                && value.compareTo(condition.getThreshold()) >= 0;
    }

    private BigDecimal segmentValue(PointSnapshot snapshot, SegmentConfig segment, PointConfig point) {
        return PointReader.decimalValue(snapshot,
                PointReader.pathResolve(segment.getPointPrefix(), point == null ? null : point.getName()));
    }

    private String trackingPath(TrimmingTrackingSection tracking, PointConfig point) {
        return PointReader.pathResolve(tracking == null ? null : tracking.getPointPrefix(),
                point == null ? null : point.getName());
    }

    private List<TrimmingResult> skip(TrackingInput input, String reason) {
        trackingStepLogger.log(input, "切边计算跳过", TrackingStepLogger.details("reason", reason));
        return new ArrayList<>();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class Material {
        private final String coilNo;
        private final Integer repeatProdNo;
        private final BigDecimal headLength;

        private Material(String coilNo, Integer repeatProdNo, BigDecimal headLength) {
            this.coilNo = coilNo;
            this.repeatProdNo = repeatProdNo;
            this.headLength = headLength;
        }
    }
}
