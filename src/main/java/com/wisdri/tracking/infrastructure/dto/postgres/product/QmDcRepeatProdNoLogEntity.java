package com.wisdri.tracking.infrastructure.dto.postgres.product;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** PostgreSQL 钢卷逐次生产记录；一条实体对应一次已分配的生产序号。 */
@Data
@TableName("qm_dc_repeat_prod_no_log")
public class QmDcRepeatProdNoLogEntity {
    /** 雪花主键，由 MyBatis-Plus 插入时分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 机组编码，入库与查询前统一大写。 */
    private String unitCode;

    /** 入口钢卷号，在同一机组内与重复生产序号共同确定记录。 */
    private String inMatNo;

    /** 从 1 开始的重复生产序号，同一机组和卷号下逐次递增。 */
    private Integer inMatRepeatProdNo;

    /** 逻辑删除标记；逐次记录不删除，固定写 0。 */
    private Integer deleted;

    /** 本次分配时间，使用应用进程本地时区，写入 PG timestamp(6) 列。 */
    private LocalDateTime createTime;
}
