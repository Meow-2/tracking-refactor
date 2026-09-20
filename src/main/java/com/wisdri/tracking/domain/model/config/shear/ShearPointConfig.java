package com.wisdri.tracking.domain.model.config.shear;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单把剪刀配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearPointConfig {
    /**
     * 剪切触发信号点名；同时作为剪刀 runtime 的唯一键和步骤日志中的 shearPoint。
     */
    private String name;
    /**
     * 剪切触发信号的数据类型；当前要求为 {@link PointDataType#BOOLEAN}。
     */
    private PointDataType type;
    /**
     * HEAD、SLICE、TAIL 三种逻辑判型对应的数据库字符串编码。
     */
    private ShearTypeCodes typeCodes;
    /**
     * 剪刀未动作时的正常位；仅“上一帧等于正常位且当前帧离开正常位”产生触发沿。
     */
    private Boolean normalPos;
    /**
     * 单设备场景绑定的 status candidates 设备代码；设备侧由本剪刀所在配置集合决定。
     * 多设备场景使用 {@link #deviceCodes} 明确匹配顺序。
     */
    private String deviceCode;
    /**
     * 本剪刀允许关联的多个 status 设备代码，按此顺序查找当前 candidates；没有匹配候选时跳过该剪切点。
     */
    private List<String> deviceCodes;
    /**
     * 该剪刀的默认判型、切头/切尾长度及飞剪取样废料参数。
     */
    private ShearSettings shearSettings;
    /**
     * 连续线出口剪刀处的物料颜色点；开卷机侧不依赖此点，出口自动判型、固定切头或固定分切选料时需要。
     */
    private PointConfig colorPoint;
    /**
     * 非连续线剪刀附近的光栅占位点；全部光栅占位时认为剪刀到设备之间连续占位。
     */
    private List<GratingPointConfig> gratingPoints;
}
