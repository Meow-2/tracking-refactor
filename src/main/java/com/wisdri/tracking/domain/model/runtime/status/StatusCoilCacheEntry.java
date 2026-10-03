package com.wisdri.tracking.domain.model.runtime.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * status 已识别钢卷的属性快照；卷号由外层缓存映射的键表示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCoilCacheEntry {
    /** 当前生产周期的重复生产次数；取号失败时为空，不得沿用上一周期的值。 */
    private Integer repeatProdNo;

    /** status 设备点位最近一次提供的颜色号；设备未提供时可为空。 */
    private String colorNo;
}
