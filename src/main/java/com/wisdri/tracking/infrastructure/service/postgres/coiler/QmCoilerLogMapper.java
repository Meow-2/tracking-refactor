package com.wisdri.tracking.infrastructure.service.postgres.coiler;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdri.tracking.infrastructure.dto.postgres.coiler.QmCoilerLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 开卷卷取方式记录 MyBatis Plus mapper。
 */
@Mapper
public interface QmCoilerLogMapper extends BaseMapper<QmCoilerLogEntity> {
}
