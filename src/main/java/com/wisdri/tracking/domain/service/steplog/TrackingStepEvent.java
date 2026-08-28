package com.wisdri.tracking.domain.service.steplog;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 单条跟踪算法步骤日志事件。
 */
@Data
@Builder
public class TrackingStepEvent {
    private Instant timestamp;
    private String unitCode;
    private TrackingType trackingType;
    private String templateCode;
    private Instant receivedAt;
    private String stage;
    private String segmentCode;
    private Map<String, Object> details;
}
