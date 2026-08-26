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
     * 剪切触发信号的数据类型；连续线当前要求为 {@link PointDataType#BOOLEAN}。
     */
    private PointDataType type;
    /**
     * HEAD、SLICE、TAIL 三种逻辑判型对应的数据库整数编码。
     */
    private ShearTypeCodes typeCodes;
    /**
     * 剪刀未动作时的正常位；仅“上一帧等于正常位且当前帧离开正常位”产生触发沿。
     */
    private Boolean normalPos;
    /**
     * 与 status runtime candidates 键完全一致的设备代码；所在配置集合决定其设备侧。
     */
    private String porTrCode;
    /**
     * 该剪刀的默认判型、切头/切尾长度及飞剪取样废料参数。
     */
    private ShearSettings shearSettings;
    /**
     * 连续线剪刀处的物料颜色点；用于与 status 中设备颜色比较，默认判型不为空时可省略。
     */
    private PointConfig colorPoint;
    /**
     * 非连续线剪刀附近的光栅占位点；当前保留配置结构，连续线算法不读取。
     */
    private List<GratingPointConfig> gratingPoints;
}
