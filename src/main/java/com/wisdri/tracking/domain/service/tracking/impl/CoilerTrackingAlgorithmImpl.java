package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.coiler.CoilerResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 status 固化的钢卷号变化结果转换为数据库结果。
 */
@Component
public class CoilerTrackingAlgorithmImpl implements TrackingAlgorithm<CoilerResult> {
    @Resource
    private TrackingStepLogger trackingStepLogger;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.COILER == trackingType;
    }

    @Override
    public List<CoilerResult> calculate(TrackingInput input) {
        long startedAt = System.nanoTime();
        if (input == null || input.getTrackingType() != TrackingType.COILER) {
            throw new IllegalArgumentException("开卷卷取跟踪输入和跟踪类型不能为空");
        }
        StatusTrackingContext context = input.getStatusContext();
        if (context == null || context.getResults() == null || context.getResults().isEmpty()) {
            throw new IllegalArgumentException("开卷卷取状态结果不能为空");
        }
        trackingStepLogger.log(input, "计算开始", TrackingStepLogger.details(
                "statusResultCount", context.getResults().size()
        ));
        List<CoilerResult> results = new ArrayList<>(context.getResults().size());
        Instant generatedAt = Instant.now();
        for (StatusResult status : context.getResults()) {
            validate(status);
            CoilerResult result = CoilerResult.builder()
                    .unitCode(status.getUnitCode())
                    .trackingType(TrackingType.COILER)
                    .generatedAt(generatedAt)
                    .receivedAt(status.getReceivedAt())
                    .inMatNo(status.getCoilNo())
                    .inMatNoProdNo(status.getProductNo())
                    .coilerMethod(DeviceSide.COILER == status.getSide()
                            ? status.getCoilerMethod() : null)
                    .coilerMethodName(DeviceSide.COILER == status.getSide()
                            ? status.getCoilerMethodName() : null)
                    .coilerDeviceCode(DeviceSide.COILER == status.getSide()
                            ? status.getDeviceCode() : null)
                    .coilerDeviceName(DeviceSide.COILER == status.getSide()
                            ? status.getDeviceName() : null)
                    .coilerMaxLength(DeviceSide.COILER == status.getSide()
                            ? status.getMaxLength() : null)
                    .uncoilerMethod(DeviceSide.UNCOILER == status.getSide()
                            ? status.getCoilerMethod() : null)
                    .uncoilerMethodName(DeviceSide.UNCOILER == status.getSide()
                            ? status.getCoilerMethodName() : null)
                    .uncoilerDeviceCode(DeviceSide.UNCOILER == status.getSide()
                            ? status.getDeviceCode() : null)
                    .uncoilerDeviceName(DeviceSide.UNCOILER == status.getSide()
                            ? status.getDeviceName() : null)
                    .uncoilerMaxLength(DeviceSide.UNCOILER == status.getSide()
                            ? status.getMaxLength() : null)
                    .build();
            results.add(result);
            trackingStepLogger.log(input, "开卷卷取结果生成", status.getDeviceCode(),
                    TrackingStepLogger.details(
                            "coilNo", status.getCoilNo(),
                            "productNo", status.getProductNo(),
                            "side", status.getSide(),
                            "coilerMethod", status.getCoilerMethod(),
                            "coilerMethodName", status.getCoilerMethodName(),
                            "deviceName", status.getDeviceName(),
                            "maxLength", status.getMaxLength()
                    ));
        }
        trackingStepLogger.log(input, "计算完成", TrackingStepLogger.details(
                "resultCount", results.size(),
                "elapsedMillis", (System.nanoTime() - startedAt) / 1_000_000L
        ));
        return results;
    }

    private void validate(StatusResult status) {
        if (status == null || blank(status.getUnitCode()) || blank(status.getCoilNo())
                || status.getSide() == null
                || blank(status.getCoilerMethod()) || blank(status.getCoilerMethodName())
                || blank(status.getDeviceCode()) || blank(status.getDeviceName())) {
            throw new IllegalArgumentException("开卷卷取状态结果业务字段不能为空");
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
