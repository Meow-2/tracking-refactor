package com.wisdri.tracking.domain.model.tracking.status;

import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 单个设备端的当前钢卷状态。
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
    private String colorNo;
    private BigDecimal remainingLength;
}
