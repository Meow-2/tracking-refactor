package com.wisdri.tracking.infrastructure.dto.feign.timeseries.history;

import lombok.Data;

/** 查询结果。 */
@Data
public class Bounds {
    private String firstTime;
    private String lastTime;
}
