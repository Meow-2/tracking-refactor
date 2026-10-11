package com.wisdri.tracking.infrastructure.dto.feign.timeseries.history;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.databind.JsonNode;

/** 查询结果。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Latest extends Point {
    private boolean found;
    private String sourceTime;
    private JsonNode value;
}
