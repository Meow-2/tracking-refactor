package com.wisdri.tracking.domain.model.tracking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 单条跟踪结果的公共父类。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class TrackingResult {
    /**
     * 机组代码，例如 CP1、ZRM1。
     */
    private String unitCode;

    /**
     * 跟踪类型。
     */
    private TrackingType trackingType;

    /**
     * 跟踪结果生成时间。
     */
    private Instant generatedAt;

    /**
     * 最新点位快照接收时间。
     */
    private Instant receivedAt;
}
