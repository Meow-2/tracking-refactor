package com.wisdri.tracking.application.retracking.command;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 数据重跟踪命令。
 *
 * <p>描述一次历史时间范围内的重跟踪请求。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReTrackingCommand {
    /**
     * 机组代码。
     */
    private String unitCode;

    /**
     * 跟踪类型。
     */
    private TrackingType trackingType;

    /**
     * 重跟踪开始时间。
     */
    private Instant startTime;

    /**
     * 重跟踪结束时间。
     */
    private Instant endTime;
}
