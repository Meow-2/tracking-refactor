package com.wisdri.tracking.infrastructure.service.postgres.product;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * PG 钢卷重复生产次数访问器；每次分配追加一条记录。
 */
@Mapper
public interface RepeatProdNoMapper {
    /** 事务级锁按机组和钢卷序列化取号；哈希碰撞只会增加等待，不会分配重复次数。 */
    @Select("SELECT 1 FROM pg_advisory_xact_lock(hashtext(#{unitCode}), hashtext(#{coilNo}))")
    Integer lockCoil(@Param("unitCode") String unitCode, @Param("coilNo") String coilNo);

    /** 查询已分配的最大次数；不存在时由 MyBatis 返回 null。 */
    @Select("SELECT in_mat_repeat_prod_no FROM public.qm_dc_repeat_prod_no_log "
            + "WHERE unit_code = #{unitCode} AND in_mat_no = #{coilNo} "
            + "ORDER BY in_mat_repeat_prod_no DESC LIMIT 1")
    Integer findLatest(@Param("unitCode") String unitCode, @Param("coilNo") String coilNo);

    /** 仅插入本次分配；唯一约束负责阻止绕过锁的重复写入。 */
    @Select("INSERT INTO public.qm_dc_repeat_prod_no_log "
            + "(id, unit_code, in_mat_no, in_mat_repeat_prod_no, deleted, create_time) "
            + "VALUES (#{id}, #{unitCode}, #{coilNo}, #{repeatProdNo}, 0, LOCALTIMESTAMP(6)) "
            + "RETURNING in_mat_repeat_prod_no")
    Integer insert(@Param("id") Long id, @Param("unitCode") String unitCode,
                   @Param("coilNo") String coilNo, @Param("repeatProdNo") Integer repeatProdNo);
}
