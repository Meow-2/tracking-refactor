package com.wisdri.tracking.infrastructure.dto.feign.timeseries;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TimeSeriesTableMode {

    COLUMN("column","列存"),
    ROW("row","行存");

    private final String code;
    private final String desc;
}
