package com.wisdri.tracking.domain.model.config.status;

import com.wisdri.tracking.domain.model.config.PointConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * status 专用的轧机道次与质量输出配置。
 * 方向、道次和质量点位均从 status 快照读取，不依赖 process 配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusRollingConfig {
    /** 轧制方向点位；按 status.tracking.point_prefix 解析。 */
    private PointConfig directPoint;

    /** 道次号点位；有效生产道次为正整数。 */
    private PointConfig passNoPoint;

    /** true 反转方向点含义；false 保持原始含义，空值按 false 处理。 */
    private Boolean directReverse;

    /** true 归集道次质量数据；false 或空值时不生成质量接口请求。 */
    private Boolean qualityOutputEnabled;

    /** 出口厚度点位，单位 mm；仅质量输出启用时读取。 */
    private PointConfig outThicknessPoint;

    /** 质量采样最低速度，实际速度须严格大于该值；仅质量输出启用时必填。 */
    private BigDecimal qualityMinSpeed;
}
