package com.wisdri.tracking.infrastructure.service.postgres.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdri.tracking.infrastructure.dto.postgres.product.QmDcRepeatProdNoLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * PG 钢卷重复生产记录 MyBatis-Plus mapper，使用通用方法读写逐次记录。
 */
@Mapper
public interface RepeatProdNoMapper extends BaseMapper<QmDcRepeatProdNoLogEntity> {
    /** 取号必须计入已逻辑删除的记录，避免复用序号并触发业务键唯一约束。 */
    @Select("SELECT MAX(in_mat_repeat_prod_no) FROM qm_dc_repeat_prod_no_log "
            + "WHERE unit_code = #{unitCode} AND in_mat_no = #{coilNo}")
    Integer selectHistoricalMaxRepeatProdNo(@Param("unitCode") String unitCode,
                                            @Param("coilNo") String coilNo);
}
