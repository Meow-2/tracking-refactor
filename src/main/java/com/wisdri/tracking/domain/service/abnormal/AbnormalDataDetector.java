package com.wisdri.tracking.domain.service.abnormal;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;

import java.util.List;

/**
 * 异常数据检测服务。
 *
 * <p>负责识别空数据、异常波动等需要持久化排查的点位数据。</p>
 */
public interface AbnormalDataDetector {
    /**
     * 检测最新快照中的异常点位数据。
     */
    List<AbnormalData> detect(PointSnapshot latest, PointSnapshot previous, TrackingConfig config);
}
