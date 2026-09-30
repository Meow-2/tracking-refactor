package com.wisdri.tracking.infrastructure.service.postgres.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * PG 钢卷生产次数访问器；递增使用单条语句和联合主键保证并发分配不重复。
 */
@Mapper
public interface ProductNoMapper {
    /** 首次插入次数 1；冲突时在行锁保护下加 1，返回更新后的次数。 */
    @Select("INSERT INTO public.qm_dc_product_no "
            + "(id, unit_code, in_mat_no, in_mat_product_no, deleted, create_time, update_time) "
            + "VALUES (#{id}, #{unitCode}, #{coilNo}, 1, 0, LOCALTIMESTAMP(6), LOCALTIMESTAMP(6)) "
            + "ON CONFLICT (unit_code, in_mat_no) DO UPDATE "
            + "SET in_mat_product_no = qm_dc_product_no.in_mat_product_no + 1, "
            + "update_time = LOCALTIMESTAMP(6), "
            + "update_user = 1831666618627928065 "
            + "RETURNING in_mat_product_no")
    Integer incrementAndGet(@Param("id") Long id, @Param("unitCode") String unitCode,
                            @Param("coilNo") String coilNo);

    /** 只读查询当前次数；不存在时由 MyBatis 返回 null。 */
    @Select("SELECT in_mat_product_no FROM public.qm_dc_product_no "
            + "WHERE unit_code = #{unitCode} AND in_mat_no = #{coilNo}")
    Integer findCurrent(@Param("unitCode") String unitCode, @Param("coilNo") String coilNo);
}
