package com.wisdri.tracking.infrastructure.dto.feign.timeseries.history;

import lombok.Data;

/** 点位的数据库、表和字段。 */
@Data
public class Point {
    private String database;
    private String table;
    private String field;
}
