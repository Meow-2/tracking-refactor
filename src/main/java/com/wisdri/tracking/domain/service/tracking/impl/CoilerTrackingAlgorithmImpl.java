package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.coiler.CoilerResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 status 固化的钢卷号变化结果转换为数据库结果。
 */
@Component
public class CoilerTrackingAlgorithmImpl implements TrackingAlgorithm<CoilerResult> {
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.COILER == trackingType;
    }

    @Override
    public List<CoilerResult> calculate(TrackingInput input) {
        if (input == null || input.getTrackingType() != TrackingType.COILER) {
            throw new IllegalArgumentException("开卷卷取跟踪输入和跟踪类型不能为空");
        }
        StatusTrackingContext context = input.getStatusContext();
        if (context == null || context.getResults() == null || context.getResults().isEmpty()) {
            throw new IllegalArgumentException("开卷卷取状态结果不能为空");
        }
        List<CoilerResult> results = new ArrayList<>(context.getResults().size());
        Instant generatedAt = Instant.now();
        for (StatusResult status : context.getResults()) {
            validate(status);
            results.add(CoilerResult.builder()
                    .unitCode(status.getUnitCode())
                    .trackingType(TrackingType.COILER)
                    .generatedAt(generatedAt)
                    .receivedAt(status.getReceivedAt())
                    .inMatNo(status.getCoilNo())
                    .inMatNoProdNo(status.getProductNo())
                    .coilerMethod(status.getCoilerMethod())
                    .coilerMethodName(status.getCoilerMethodName())
                    .deviceCode(status.getDeviceCode())
                    .deviceName(status.getDeviceName())
                    .maxLength(status.getMaxLength())
                    .build());
        }
        return results;
    }

    private void validate(StatusResult status) {
        if (status == null || blank(status.getUnitCode()) || blank(status.getCoilNo())
                || blank(status.getCoilerMethod()) || blank(status.getCoilerMethodName())
                || blank(status.getDeviceCode()) || blank(status.getDeviceName())) {
            throw new IllegalArgumentException("开卷卷取状态结果业务字段不能为空");
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
