package com.wisdri.tracking.domain.service.abnormal.impl;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.abnormal.AbnormalType;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.abnormal.AbnormalDataRepository;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 默认异常数据处理实现。
 */
@Component
public class AbnormalDataHandlerImpl implements AbnormalDataHandler {
    /**
     * 异常数据存储端口。
     */
    @Resource
    private AbnormalDataRepository abnormalDataRepository;

    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingConfigRepository configRepository;

    /**
     * 默认支持全部跟踪类型。
     */
    @Override
    public boolean support(TrackingType trackingType) {
        return true;
    }

    /**
     * 检测并保存异常数据。
     */
    @Override
    public void handle(TrackingInput input) {
        List<AbnormalData> abnormalData = detect(input);
        if (abnormalData.isEmpty()) {
            return;
        }
        abnormalDataRepository.save(abnormalData);
    }

    /**
     * 检测最新快照中的空点位数据。
     */
    private List<AbnormalData> detect(TrackingInput input) {
        List<AbnormalData> result = new ArrayList<>();
        TrackingConfig config = configRepository.refresh(input.getUnitCode(), input.getTrackingType()).orElse(null);
        if (config == null) {
            return result;
        }
        PointSnapshot latest = input.getLatestSnapshot();
        if (latest == null || latest.getValues() == null) {
            return result;
        }
        for (Map.Entry<String, PointValue> entry : latest.getValues().entrySet()) {
            Object rawValue = entry.getValue() == null ? null : entry.getValue().getRawValue();
            if (rawValue == null || String.valueOf(rawValue).trim().isEmpty()) {
                result.add(AbnormalData.builder()
                        .unitCode(config.getUnitCode())
                        .trackingType(config.getTrackingType())
                        .pointCode(entry.getKey())
                        .abnormalType(AbnormalType.NULL_DATA)
                        .rawValue(rawValue)
                        .reason("点位值为空")
                        .occurredAt(Instant.now())
                        .build());
            }
        }
        return result;
    }
}
