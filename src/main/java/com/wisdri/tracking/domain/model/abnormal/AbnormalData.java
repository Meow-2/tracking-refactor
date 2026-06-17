package com.wisdri.tracking.domain.model.abnormal;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 异常点位数据记录。
 *
 * <p>用于记录空数据、异常波动等需要后续持久化或排查的数据。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbnormalData {
    /**
     * 机组代码。
     */
    private String unitCode;

    /**
     * 跟踪类型。
     */
    private TrackingType trackingType;

    /**
     * 异常点位编码或点位短名。
     */
    private String pointCode;

    /**
     * 异常类型。
     */
    private AbnormalType abnormalType;

    /**
     * 异常点位原始值。
     */
    private Object rawValue;

    /**
     * 异常原因说明。
     */
    private String reason;

    /**
     * 异常发生时间。
     */
    private Instant occurredAt;
}
