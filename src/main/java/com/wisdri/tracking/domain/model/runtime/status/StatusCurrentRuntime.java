package com.wisdri.tracking.domain.model.runtime.status;

import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 某一设备端当前识别状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCurrentRuntime {
    private DeviceSide side;
    private Boolean running;
    private String deviceCode;
    private String deviceName;
    private String coilNo;
    private String colorNo;
    private BigDecimal remainingLength;
}
