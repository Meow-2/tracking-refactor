package com.wisdri.tracking.domain.service.point;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointEvent;

import java.util.List;

/**
 * 跟踪事件检测服务。
 *
 * <p>负责比较最新快照和上一条快照，识别钢卷变化等业务事件。</p>
 */
public interface PointEventDetector {
    /**
     * 检测本次点位变化触发的业务事件。
     */
    List<PointEvent> detect(PointSnapshot latest, PointSnapshot previous, TrackingConfig config);
}
