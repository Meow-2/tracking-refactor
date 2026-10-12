package com.wisdri.tracking.domain.model.tracking.status;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wisdri.tracking.common.utils.ShanghaiLocalInstantSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** 已结束道次的质量接口数据；提交后允许异步发送失败而不影响状态跟踪。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollingPassOutput {
    /** 机组代码，例如 ZRM1。 */
    private String unitCode;
    /** 入口物料跟踪号。 */
    private String inMatNo;
    /** 入口物料重复生产次数号。 */
    private Integer inMatRepeatProdNo;
    /** 由旧道次号生成的加工单元代码。 */
    private String cellCode;
    /** 首个完整有效帧的绝对时间；JSON 展示为上海本地 ISO 时间，无时区后缀。 */
    @JsonSerialize(using = ShanghaiLocalInstantSerializer.class)
    private Instant startAt;
    /** 最后一个完整有效帧的绝对时间；JSON 展示为上海本地 ISO 时间，无时区后缀。 */
    @JsonSerialize(using = ShanghaiLocalInstantSerializer.class)
    private Instant endAt;
    /** 出口厚度，单位 mm。 */
    private BigDecimal outMatThick;
    /** 卷取端出口长度，单位 m。 */
    private BigDecimal outMatLength;
}
