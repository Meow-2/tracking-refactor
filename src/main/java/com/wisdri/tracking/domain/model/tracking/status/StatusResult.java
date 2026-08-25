package com.wisdri.tracking.domain.model.tracking.status;

import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单个设备端的当前钢卷状态结果。
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
    private String coilNo;
    private Integer productNo;
    private String colorNo;
    private BigDecimal remainingLength;
    private BigDecimal maxLength;

    /**
     * 将状态运行态转换为算法结果，并补充结果公共元数据。
     */
    public static StatusResult from(StatusCurrentRuntime current,
                                    String unitCode,
                                    Instant generatedAt,
                                    Instant receivedAt) {
        if (current == null) {
            return null;
        }
        return StatusResult.builder()
                .unitCode(unitCode)
                .trackingType(TrackingType.STATUS)
                .generatedAt(generatedAt)
                .receivedAt(receivedAt)
                .side(current.getSide())
                .running(current.getRunning())
                .deviceCode(current.getDeviceCode())
                .deviceName(current.getDeviceName())
                .coilNo(current.getCoilNo())
                .productNo(current.getProductNo())
                .colorNo(current.getColorNo())
                .remainingLength(current.getRemainingLength())
                .maxLength(current.getMaxLength())
                .build();
    }
}
