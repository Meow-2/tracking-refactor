package com.wisdri.tracking.domain.model.runtime.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 当前轧制道次最后一次完整有效采样，供换道时生成质量接口请求。
 * 同一对象的厚度、长度和结束时间来自同一帧，避免跨帧拼接。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollingPassOutputState {
    /** 有效道次号，范围 1～999。 */
    private Integer passNo;
    /** 首个完整有效生产帧的接收时间，内部使用 UTC Instant。 */
    private Instant startAt;
    /** 最后一个完整有效生产帧的接收时间，内部使用 UTC Instant。 */
    private Instant endAt;
    /** 本道次开卷端入口物料跟踪号。 */
    private String inMatNo;
    /** 本道次开卷端入口物料重复生产次数。 */
    private Integer inMatRepeatProdNo;
    /** 最后有效帧的出口厚度，单位 mm。 */
    private BigDecimal outMatThick;
    /** 最后有效帧的卷取端累计长度，单位 m。 */
    private BigDecimal outMatLength;
}
