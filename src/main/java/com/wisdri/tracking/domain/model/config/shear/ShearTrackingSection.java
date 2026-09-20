package com.wisdri.tracking.domain.model.config.shear;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 通用剪切跟踪算法配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearTrackingSection {
    /**
     * 剪切 MQTT 点位的统一路径前缀；算法以“前缀 + 点位 name”读取快照值。
     */
    private String pointPrefix;
    /**
     * 生产线判型模式；连续线读取颜色点，非连续线读取光栅占位点。
     */
    private ShearMode mode;
    /**
     * 切尾经验阈值，单位与 status 中开卷机长度一致；用于判断开卷机侧切尾和切头。
     */
    private BigDecimal tailExperience;
    /**
     * 完整剪切分组阈值，单位与开卷机剩余长度一致；剩余长度差大于等于该值时 shearNo 加一。
     */
    private BigDecimal shearExperience;
    /**
     * 位于开卷机侧的剪刀配置，列表顺序决定同一帧多剪刀事件的计算顺序。
     */
    private List<ShearPointConfig> uncoilerShearPoint;
    /**
     * 位于卷取机侧的剪刀配置，列表顺序决定同一帧多剪刀事件的计算顺序。
     */
    private List<ShearPointConfig> coilerShearPoint;
}
