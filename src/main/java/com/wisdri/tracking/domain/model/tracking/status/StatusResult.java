package com.wisdri.tracking.domain.model.tracking.status;

import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单台设备检测到钢卷号变化时生成的状态结果。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StatusResult extends TrackingResult {
    private DeviceSide side;
    private Boolean running;
    private String deviceCode;
    private String deviceName;
    private String coilerMethod;
    private String coilerMethodName;
    private String coilNo;
    private Integer productNo;
    private String colorNo;
    private BigDecimal remainingLength;
    private BigDecimal maxLength;

    /**
     * 将发生卷号变化的候选状态转换为开卷卷取跟踪输入结果。
     */
    public static StatusResult from(StatusCandidateRuntime candidate,
                                    StatusPointGroup group,
                                    String unitCode,
                                    Instant generatedAt,
                                    Instant receivedAt) {
        if (candidate == null || group == null) {
            return null;
        }
        BigDecimal remainingLength = candidate.getLengths() == null
                || candidate.getLengths().isEmpty()
                ? null : candidate.getLengths().get(candidate.getLengths().size() - 1);
        return StatusResult.builder()
                .unitCode(unitCode)
                .trackingType(TrackingType.STATUS)
                .generatedAt(generatedAt)
                .receivedAt(receivedAt)
                .side(group.getSide())
                .running(null)
                .deviceCode(group.getCode())
                .deviceName(group.getName())
                .coilerMethod(candidate.getCoilerMethod())
                .coilerMethodName(candidate.getCoilerMethodName())
                .coilNo(candidate.getCoilNo())
                .productNo(candidate.getProductNo())
                .colorNo(candidate.getColorNo())
                .remainingLength(remainingLength)
                .maxLength(candidate.getMaxLength())
                .build();
    }
}
