package com.wisdri.tracking.domain.model.tracking;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrackingType {

    PROCESS("process","过程跟踪"),
    BATCH("batch", "批次跟踪"),
    STATUS("status", "状态跟踪"),
    COILER("coiler", "开卷卷取跟踪"),
    SHEAR("shear","剪切跟踪"),
    TRIMMING("trimming","切边跟踪"),
    IRONLOSS("ironloss", "铁损跟踪");

    private final String code;
    private final String desc;
}
