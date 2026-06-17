package com.wisdri.tracking.domain.service.abnormal.impl;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.repository.abnormal.AbnormalDataStorage;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 默认异常数据处理实现。
 */
@Component
public class AbnormalDataHandlerImpl implements AbnormalDataHandler {
    /**
     * 异常数据存储端口。
     */
    @Resource
    private AbnormalDataStorage abnormalDataStorage;

    /**
     * 保存检测出的异常数据。
     */
    @Override
    public void handle(TrackingInput input, List<AbnormalData> abnormalData) {
        if (abnormalData == null || abnormalData.isEmpty()) {
            return;
        }
        abnormalDataStorage.save(abnormalData);
    }
}
