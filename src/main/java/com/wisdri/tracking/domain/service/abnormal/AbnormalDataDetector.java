package com.wisdri.tracking.domain.service.abnormal;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.List;

/**
 * 异常数据处理服务。
 *
 * <p>负责按跟踪类型检测并处理异常数据。</p>
 */
public interface AbnormalDataDetector<C extends TrackingConfig> {

    /**
     * 是否支持指定跟踪类型。
     */
    boolean support(TrackingType trackingType);

    /**
     * 检测最新快照中的异常点位数据。
     */
    List<AbnormalData> detect(TrackingInput input, C config);
}
