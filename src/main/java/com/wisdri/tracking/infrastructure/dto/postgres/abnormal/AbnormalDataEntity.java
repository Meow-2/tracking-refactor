package com.wisdri.tracking.infrastructure.dto.postgres.abnormal;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * PostgreSQL 异常数据表实体。
 */
@Data
@TableName("abnormal_data")
public class AbnormalDataEntity {
    /**
     * 主键。
     */
    @TableId
    private Long id;

    /**
     * 机组代码。
     */
    private String unitCode;

    /**
     * 跟踪类型编码。
     */
    private String trackingType;

    /**
     * 异常点位编码。
     */
    private String pointCode;

    /**
     * 异常类型编码。
     */
    private String abnormalType;

    /**
     * 异常点位原始值。
     */
    private String rawValue;

    /**
     * 异常原因说明。
     */
    private String reason;

    /**
     * 异常发生时间。
     */
    private Instant occurredAt;
}
