package com.wisdri.tracking.infrastructure.dto.postgres.trimming;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * PostgreSQL 圆盘剪切边量记录。
 */
@Data
@TableName("qm_dc_trimming_log")
public class QmTrimmingLogEntity {
    @TableId
    private Long id;
    private String unitCode;
    private String inMatNo;
    /** 切边归属钢卷的重复生产序号；沿用记录表的字符串列类型，允许为空。 */
    @TableField("in_mat_repeat_prod_no")
    private String inMatRepeatProdNo;
    private String coilWidthPv;
    private String coilWidthSv;
    private String trimmingLength;
    private Instant createTime;
    private Instant updateTime;
}
