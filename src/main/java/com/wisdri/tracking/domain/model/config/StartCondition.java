package com.wisdri.tracking.domain.model.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 跟踪启动条件配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartCondition {
    /**
     * 用于判断启动条件的点位。
     */
    private PointConfig point;

    /**
     * 点位值大于等于该阈值时允许继续跟踪。
     */
    private BigDecimal threshold;
}
