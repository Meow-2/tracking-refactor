package com.wisdri.tracking.domain.model.config.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 跟踪启动条件配置。
 *
 * <p>当指定点位值达到阈值后，算法才继续生成跟踪结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartCondition {
    /**
     * 用于判断启动条件的点位配置。
     */
    private PointConfig point;

    /**
     * 启动阈值，点位值大于等于该值时允许继续跟踪。
     */
    private BigDecimal threshold;
}
