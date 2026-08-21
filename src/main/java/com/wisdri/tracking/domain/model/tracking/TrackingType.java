package com.wisdri.tracking.domain.model.tracking;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrackingType {

    PROCESS("process","过程跟踪"),
    BATCH("batch", "批次跟踪"),
    STATUS("status", "状态跟踪"),
    SHEAR("shear","剪切跟踪"),
    UNCOILER("uncoiler","开卷跟踪"),
    WELDING("welding","焊接跟踪"),
    TRIMMING("trimming","切边跟踪");

    private final String code;
    private final String desc;
}
