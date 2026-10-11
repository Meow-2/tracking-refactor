package com.wisdri.tracking.infrastructure.dto.feign.timeseries.history;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/** 查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BoundsRequest extends Point {
    private BigDecimal min;
    private String startTime;
    private String endTime;
}
