package com.wisdri.tracking.infrastructure.dto.feign.timeseries.history;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StatesRequest extends Point {
    private String startTime;
    private String endTime;
}
