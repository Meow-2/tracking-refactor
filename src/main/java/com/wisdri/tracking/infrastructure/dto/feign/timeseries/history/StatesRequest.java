package com.wisdri.tracking.infrastructure.dto.feign.timeseries.history;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StatesRequest extends Point {
    private String startTime;
    private String endTime;
}
