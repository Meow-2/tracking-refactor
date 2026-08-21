package com.wisdri.tracking.domain.model.config.status;

import com.wisdri.tracking.domain.model.config.PointConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单台开卷机或卷取机的卷号、剩余长度点位组合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusPointGroup {
    private String code;
    private String name;
    private DeviceSide side;
    private PointConfig coilNo;
    private PointConfig remainingLength;
}
