package com.wisdri.tracking.domain.service.abnormal;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.repository.abnormal.AbnormalDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class AbnormalDataHandlerDispatcher {

    @Resource
    private List<AbnormalDataDetector<? extends TrackingConfig>> detectors;

    /**
     * 异常数据存储端口。
     */
    @Resource
    private AbnormalDataRepository abnormalDataRepository;

    /**
     * 处理最新快照中的异常点位数据。
     */
    @SuppressWarnings("unchecked")
    public <C extends TrackingConfig> void handle(TrackingInput input, C config) {
        if (input == null || config == null || detectors == null) {
            return;
        }
        List<AbnormalData> abnormalDataList = new ArrayList<>();
        for (AbnormalDataDetector<? extends TrackingConfig> detector : detectors) {
            if (detector.support(input.getTrackingType())) {
                abnormalDataList = ((AbnormalDataDetector<C>)detector).detect(input, config);
            }
        }
        try {
            abnormalDataRepository.save(abnormalDataList);
        } catch (RuntimeException e) {
            log.error("{}: 保存异常数据失败，数量={}", input.getTrackingType(), abnormalDataList.size(), e);
        }
    }
}
