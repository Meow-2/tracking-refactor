package com.wisdri.tracking.infrastructure.dto.postgres.coiler;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * PostgreSQL 钢卷开卷卷取方式记录。
 */
@Data
@TableName("qm_dc_coiler_log")
public class QmCoilerLogEntity {
    @TableId
    private Long id;
    private String unitCode;
    private String inMatNo;
    /** 入口钢卷本次重复生产序号；沿用记录表的字符串列类型，允许为空。 */
    @TableField("in_mat_repeat_prod_no")
    private String inMatRepeatProdNo;
    private Integer passNo;
    private String coilerMethod;
    private String coilerMethodName;
    private String coilerDeviceCode;
    private String coilerDeviceName;
    private String coilerMaxLength;
    private String uncoilerMethod;
    private String uncoilerMethodName;
    private String uncoilerDeviceCode;
    private String uncoilerDeviceName;
    private String uncoilerMaxLength;
    private Instant createTime;
}
