package com.wisdri.tracking.domain.service.abnormal;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;

import java.util.List;

/**
 * 异常数据处理服务。
 *
 * <p>负责处理异常检测结果，检测器只判断异常是否存在。</p>
 */
public interface AbnormalDataHandler {
    /**
     * 处理本次跟踪输入检测出的异常数据。
     */
    void handle(TrackingInput input, List<AbnormalData> abnormalData);
}
