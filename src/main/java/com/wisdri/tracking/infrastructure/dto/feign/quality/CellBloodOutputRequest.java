package com.wisdri.tracking.infrastructure.dto.feign.quality;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 单机架轧机旧道次的机组内物料写入请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CellBloodOutputRequest {
    /** 机组代码。 */
    private String unitCode;
    /** 入口物料跟踪号。 */
    private String inMatNo;
    /** 入口物料生产次数号，按接口约定传字符串。 */
    private String inMatRepeatProdNo;
    /** 机组代码加三位旧道次序号。 */
    private String cellCode;
    /** 上海本地开始时间，格式 yyyy-MM-dd HH:mm:ss。 */
    private String startProdTime;
    /** 上海本地结束时间，格式 yyyy-MM-dd HH:mm:ss。 */
    private String endProdTime;
    /** 出口厚度，单位 mm。 */
    private BigDecimal outMatThick;
    /** 出口长度，单位 m。 */
    private BigDecimal outMatLength;
}
