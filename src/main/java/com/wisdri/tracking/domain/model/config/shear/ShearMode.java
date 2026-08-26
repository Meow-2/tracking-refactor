package com.wisdri.tracking.domain.model.config.shear;

/**
 * 剪切跟踪判别模式。
 */
public enum ShearMode {
    /** 连续生产线，使用设备颜色、剪刀处颜色和开卷机剩余长度判型。 */
    CONTINUOUS,
    /** 非连续生产线，使用光栅占位状态判型；当前版本尚未接入计算策略。 */
    DISCONTINUOUS
}
