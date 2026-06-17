package com.wisdri.tracking.domain.model.tracking;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 跟踪算法输入。
 *
 * <p>算法只依赖最新快照、上一条快照和业务配置，不直接依赖 MQTT、Redis 或 RocketMQ。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingInput {
    /**
     * 本次算法使用的跟踪配置。
     */
    private TrackingConfig config;

    /**
     * 最新点位快照。
     */
    private PointSnapshot latestSnapshot;

    /**
     * 上一条点位快照。
     */
    private PointSnapshot previousSnapshot;

    /**
     * 从跟踪任务转换为算法输入。
     */
    public static TrackingInput of(TrackingTask task, TrackingConfig config) {
        return TrackingInput.builder()
                .config(config)
                .latestSnapshot(task.getLatestSnapshot())
                .previousSnapshot(task.getPreviousSnapshot())
                .build();
    }
}
