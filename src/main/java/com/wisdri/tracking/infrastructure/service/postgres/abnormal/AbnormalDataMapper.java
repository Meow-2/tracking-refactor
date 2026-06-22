package com.wisdri.tracking.infrastructure.service.postgres.abnormal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wisdri.tracking.infrastructure.dto.postgres.abnormal.AbnormalDataEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常数据 MyBatis Plus mapper。
 */
@Mapper
public interface AbnormalDataMapper extends BaseMapper<AbnormalDataEntity> {
}
