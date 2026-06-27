package com.wisdri.tracking.infrastructure.dto.feign.timeseries;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TimeSeriesDataType {

    FLOAT("float","浮点型"),
    STRING("string","字符串"),
    INT("int","整型");

    private final String code;
    private final String desc;
}
