package com.wisdri.tracking.infrastructure.dto.feign.timeseries.history;

import lombok.Data;
import java.util.List;

/** 查询参数。 */
@Data
public class LatestRequest {
    private String atTime;
    private List<Point> points;
}
