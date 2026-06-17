package com.wisdri.tracking.domain.model.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LengthMode {

    WELDER("welder", "焊缝跟踪模式"),
    COILER("coiler", "卷取机长度模式"),
    ROLLING("rolling", "轧机模式");

    private final String code;
    private final String desc;
}
