package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossSegmentConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.ironloss.IronLossResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.CellCodeResolver;
import com.wisdri.tracking.domain.service.tracking.StatusRepeatProdNoResolver;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 铁损跟踪：每帧直接读取物料点位，再为各工艺段保留原始参数。 */
@Component
public class IronLossTrackingAlgorithmImpl implements TrackingAlgorithm<IronLossResult> {
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.IRONLOSS == trackingType;
    }

    /**
     * 启动条件沿用 PROCESS 的大于等于阈值语义；卷号或长度缺失时不生成记录。
     * 生产次数可以为空，表示 STATUS 尚未识别该钢卷。
     */
    @Override
    public List<IronLossResult> calculate(TrackingInput input) {
        if (input == null || input.getLatestSnapshot() == null) {
            return Collections.emptyList();
        }
        IronLossTrackingConfig config = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(), TrackingType.IRONLOSS, IronLossTrackingConfig.class).orElse(null);
        if (config == null || config.getTracking() == null || config.getSegments() == null) {
            return Collections.emptyList();
        }
        IronLossTrackingSection tracking = config.getTracking();
        PointSnapshot snapshot = input.getLatestSnapshot();
        if (!started(snapshot, tracking)) {
            return Collections.emptyList();
        }
        String coilNo = PointReader.stringValue(snapshot, path(tracking.getPointPrefix(), tracking.getCoilNo()));
        BigDecimal length = decimalValue(snapshot, path(tracking.getPointPrefix(), tracking.getLength()));
        if (coilNo == null || coilNo.trim().isEmpty() || length == null) {
            return Collections.emptyList();
        }
        Integer repeatProdNo = StatusRepeatProdNoResolver.find(input.getStatusContext(), coilNo);
        List<IronLossResult> results = new ArrayList<>();
        for (IronLossSegmentConfig segment : config.getSegments()) {
            results.add(IronLossResult.builder()
                    .unitCode(input.getUnitCode())
                    .trackingType(TrackingType.IRONLOSS)
                    .generatedAt(Instant.now())
                    .receivedAt(snapshot.getReceivedAt())
                    .segmentCode(segment.getCode())
                    .coilNo(coilNo)
                    .headLength(length)
                    .repeatProdNo(repeatProdNo)
                    .cellCode(CellCodeResolver.resolve(input.getUnitCode(), snapshot,
                            segment.getPointPrefix(), segment.getCellCodeValue(), segment.getCellCodePoint()))
                    .parameters(parameters(snapshot, segment))
                    .build());
        }
        return results;
    }

    private boolean started(PointSnapshot snapshot, IronLossTrackingSection tracking) {
        StartCondition condition = tracking.getStartCondition();
        if (condition == null) {
            return true;
        }
        BigDecimal value = decimalValue(snapshot, path(tracking.getPointPrefix(), condition.getPoint()));
        return value != null && condition.getThreshold() != null
                && value.compareTo(condition.getThreshold()) >= 0;
    }

    private BigDecimal decimalValue(PointSnapshot snapshot, String pointPath) {
        try {
            return PointReader.decimalValue(snapshot, pointPath);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 只写当前消息中有值的段参数，固定 cell_code 列不由原始参数覆盖。 */
    private Map<String, Object> parameters(PointSnapshot snapshot, IronLossSegmentConfig segment) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (segment.getPoints() == null) {
            return parameters;
        }
        for (PointConfig point : segment.getPoints()) {
            if ("cell_code".equalsIgnoreCase(point.getName())) {
                continue;
            }
            Object value = PointReader.rawValue(snapshot, path(segment.getPointPrefix(), point));
            if (value != null) {
                parameters.put(point.getName(), value);
            }
        }
        return parameters;
    }

    private String path(String prefix, PointConfig point) {
        return PointReader.pathResolve(prefix, point == null ? null : point.getName());
    }
}
