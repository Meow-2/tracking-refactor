package com.wisdri.tracking.domain.model.abnormal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AbnormalType {

    NULL_DATA("null","空数据"),
    SHARP_DATA("sharp","大幅波动数据");

    private final String code;
    private final String desc;
}
