package com.wisdri.tracking.infrastructure.service.postgres.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdri.tracking.infrastructure.dto.postgres.product.QmDcRepeatProdNoLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * PG 钢卷重复生产记录 MyBatis-Plus mapper，使用通用方法读写逐次记录。
 */
@Mapper
public interface RepeatProdNoMapper extends BaseMapper<QmDcRepeatProdNoLogEntity> {
}
