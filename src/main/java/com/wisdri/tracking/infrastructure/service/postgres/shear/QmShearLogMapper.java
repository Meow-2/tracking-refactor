package com.wisdri.tracking.infrastructure.service.postgres.shear;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdri.tracking.infrastructure.dto.postgres.shear.QmShearLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 剪切过程记录 MyBatis Plus mapper。
 */
@Mapper
public interface QmShearLogMapper extends BaseMapper<QmShearLogEntity> {
}
