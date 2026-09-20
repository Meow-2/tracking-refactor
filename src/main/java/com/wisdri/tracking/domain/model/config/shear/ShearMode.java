package com.wisdri.tracking.domain.model.config.shear;

/**
 * 剪切跟踪判别模式。
 */
public enum ShearMode {
    /** 连续生产线，使用设备颜色、剪刀处颜色和开卷机剩余长度判型。 */
    CONTINUOUS,
    /** 非连续生产线，按开卷机长度、卷取机卷号和开卷机侧光栅归属判型。 */
    DISCONTINUOUS
}
