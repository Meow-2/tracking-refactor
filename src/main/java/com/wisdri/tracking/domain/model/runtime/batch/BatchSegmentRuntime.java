package com.wisdri.tracking.domain.model.runtime.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单个 fb 的单侧卷身份；空帧和取号失败只影响该侧。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSegmentRuntime {
    /** 当前保留的卷号；为空表示该侧尚未上卷或已下卷。 */
    private String coilNo;

    /** 已写入 PG 的生产序号；旧运行态或取号失败时可为空。 */
    private Integer repeatProdNo;

    /** 与 status 一致：有效卷号为 1，缺值逐帧加 1，超过阈值后清空并重置为 1。 */
    private Integer nullCount;

    /** true 表示新卷取号失败，下一有效帧须重试；false 表示补查旧次数，缺记录时插入首次记录。 */
    private Boolean allocationPending;
}
