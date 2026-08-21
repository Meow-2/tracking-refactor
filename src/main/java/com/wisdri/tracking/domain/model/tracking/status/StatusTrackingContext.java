package com.wisdri.tracking.domain.model.tracking.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 生产跟踪任务时固化的开卷机、卷取机钢卷状态上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusTrackingContext {
    /**
     * 状态算法所使用的点位快照接收时间。
     */
    private Instant receivedAt;

    /**
     * 当前开卷机钢卷号；未识别到运行钢卷时为空。
     */
    private String uncoilerCoilNo;

    /**
     * 当前卷取机钢卷号；未识别到运行钢卷时为空。
     */
    private String coilerCoilNo;
}
