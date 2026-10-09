package com.wisdri.tracking.domain.model.tracking.status;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    private Integer passNo;
    /** 状态结果所属加工单元代码；道次为空时使用默认序号 1，越界时为空。 */
    private String cellCode;
    private String deviceCode;
    private String deviceName;
    private String coilerMethod;
    private String coilerMethodName;
    private String coilNo;
    @JsonAlias({"productNo", "product_no"})
    private Integer repeatProdNo;
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
        return from(candidate, group, group == null ? null : group.getSide(), null,
                unitCode, generatedAt, receivedAt);
    }

    /**
     * 使用当前实际侧别和道次号生成开卷卷取跟踪输入结果。
     */
    public static StatusResult from(StatusCandidateRuntime candidate,
                                    StatusPointGroup group,
                                    DeviceSide side,
                                    Integer passNo,
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
                .side(side)
                .running(null)
                .passNo(passNo)
                .deviceCode(candidate.getDeviceCode() == null
                        ? group.getCode() : candidate.getDeviceCode())
                .deviceName(candidate.getDeviceName() == null
                        ? group.getName() : candidate.getDeviceName())
                .coilerMethod(candidate.getCoilerMethod())
                .coilerMethodName(candidate.getCoilerMethodName())
                .coilNo(candidate.getCoilNo())
                .repeatProdNo(candidate.getRepeatProdNo())
                .colorNo(candidate.getColorNo())
                .remainingLength(remainingLength)
                .maxLength(candidate.getMaxLength())
                .build();
    }
}
