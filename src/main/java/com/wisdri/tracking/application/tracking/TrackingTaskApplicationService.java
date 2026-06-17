package com.wisdri.tracking.application.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingTask;

/**
 * 跟踪任务处理应用服务。
 *
 * <p>负责消费已派发的跟踪任务，编排异常检测、算法计算和结果存储。</p>
 */
public interface TrackingTaskApplicationService {
    /**
     * 处理一次跟踪任务。
     */
    void handle(TrackingTask task);
}
