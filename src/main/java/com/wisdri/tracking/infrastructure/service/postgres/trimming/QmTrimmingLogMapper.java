package com.wisdri.tracking.infrastructure.service.postgres.trimming;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdri.tracking.infrastructure.dto.postgres.trimming.QmTrimmingLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 切边量记录 MyBatis Plus mapper。
 */
@Mapper
public interface QmTrimmingLogMapper extends BaseMapper<QmTrimmingLogEntity> {
}
