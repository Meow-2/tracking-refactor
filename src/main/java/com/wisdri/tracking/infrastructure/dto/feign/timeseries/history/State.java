package com.wisdri.tracking.infrastructure.dto.feign.timeseries.history;

import lombok.Data;
import com.fasterxml.jackson.databind.JsonNode;

/** 查询结果。 */
@Data
public class State {
    private JsonNode value;
    private String firstTime;
    private String lastTime;
}
