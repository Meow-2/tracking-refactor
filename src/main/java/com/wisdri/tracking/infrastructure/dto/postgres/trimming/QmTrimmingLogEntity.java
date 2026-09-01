package com.wisdri.tracking.infrastructure.dto.postgres.trimming;

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
    private String inMatNoProdNo;
    private String coilWidthPv;
    private String coilWidthSv;
    private String trimmingLength;
    private Instant createTime;
    private Instant updateTime;
}
