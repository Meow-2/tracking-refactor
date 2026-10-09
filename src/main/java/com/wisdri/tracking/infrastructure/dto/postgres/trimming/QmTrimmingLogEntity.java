package com.wisdri.tracking.infrastructure.dto.postgres.trimming;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
    /** 逻辑删除标记：0 表示有效，1 表示已删除；新记录显式写入 0。 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
