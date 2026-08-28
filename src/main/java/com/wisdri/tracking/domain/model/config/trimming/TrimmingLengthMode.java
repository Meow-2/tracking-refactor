package com.wisdri.tracking.domain.model.config.trimming;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 切边跟踪获取当前物料的方式。
 */
@Getter
@RequiredArgsConstructor
public enum TrimmingLengthMode {
    STATUS("status", "状态跟踪模式"),
    WELDER("welder", "焊缝跟踪模式");

    private final String code;
    private final String desc;
}
