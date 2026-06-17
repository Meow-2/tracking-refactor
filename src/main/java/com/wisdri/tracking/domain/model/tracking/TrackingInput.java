package com.wisdri.tracking.domain.model.tracking;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 跟踪算法输入。
 *
 * <p>算法只依赖任务标识和点位快照，执行时自行读取当前有效配置。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingInput {
    /**
     * 机组代码。
     */
    private String unitCode;

    /**
     * 跟踪类型。
     */
    private TrackingType trackingType;

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
    public static TrackingInput of(TrackingTask task) {
        return TrackingInput.builder()
                .unitCode(task.getUnitCode())
                .trackingType(task.getTrackingType())
                .latestSnapshot(task.getLatestSnapshot())
                .previousSnapshot(task.getPreviousSnapshot())
                .build();
    }
}
