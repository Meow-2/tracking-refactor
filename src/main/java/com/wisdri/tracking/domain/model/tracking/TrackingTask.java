package com.wisdri.tracking.domain.model.tracking;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 跟踪处理任务。
 *
 * <p>用于把 MQTT 接收侧整理出的业务数据交给后续处理流程，第一版可由 RocketMQ 承载。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingTask {
    /**
     * 机组代码。
     */
    private String unitCode;

    /**
     * 跟踪类型。
     */
    private TrackingType trackingType;

    /**
     * 模板实例编码，批次跟踪使用，例如 fb1；其他跟踪类型为空。
     */
    private String templateCode;

    /**
     * 最新点位快照。
     */
    private PointSnapshot latestSnapshot;

    /**
     * 上一条点位快照。
     */
    private PointSnapshot previousSnapshot;

    /**
     * 任务生产时固化的开卷机、卷取机钢卷状态。
     */
    private StatusTrackingContext statusContext;

    /**
     * 任务发布时间。
     */
    private Instant publishedAt;
}
