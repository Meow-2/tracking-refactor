package com.wisdri.tracking.infrastructure.repository.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wisdri.tracking.domain.repository.product.RepeatProdNoRepository;
import com.wisdri.tracking.infrastructure.dto.postgres.product.QmDcRepeatProdNoLogEntity;
import com.wisdri.tracking.infrastructure.repository.PostgresUnitCode;
import com.wisdri.tracking.infrastructure.service.postgres.product.RepeatProdNoMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

/**
 * 基于 PostgreSQL 的钢卷重复生产次数仓储，逐次保存并查询最大序号。
 */
@Repository
public class RepeatProdNoRepositoryImpl
        extends ServiceImpl<RepeatProdNoMapper, QmDcRepeatProdNoLogEntity>
        implements RepeatProdNoRepository {
    @Override
    public Integer allocateNext(String unitCode, String coilNo) {
        validateKey(unitCode, coilNo);
        String storedUnitCode = PostgresUnitCode.uppercase(unitCode);
        // 同一机组同一卷只由一个请求取号；表唯一约束仍阻止意外的重复序号。
        Integer latest = findLatestStored(storedUnitCode, coilNo);
        if (latest != null && latest == Integer.MAX_VALUE) {
            throw new IllegalStateException("钢卷重复生产次数已达到整数上限: 机组=" + unitCode + "，钢卷=" + coilNo);
        }
        int next = latest == null ? 1 : latest + 1;
        QmDcRepeatProdNoLogEntity entity = new QmDcRepeatProdNoLogEntity();
        entity.setUnitCode(storedUnitCode);
        entity.setInMatNo(coilNo);
        entity.setInMatRepeatProdNo(next);
        entity.setDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        if (baseMapper.insert(entity) != 1) {
            throw new IllegalStateException("保存钢卷重复生产次数失败: 机组=" + unitCode + "，钢卷=" + coilNo);
        }
        return next;
    }

    @Override
    public Integer findLatest(String unitCode, String coilNo) {
        validateKey(unitCode, coilNo);
        Integer latest = findLatestStored(PostgresUnitCode.uppercase(unitCode), coilNo);
        return latest == null ? 1 : latest;
    }

    @Override
    public Integer findLatestOrAllocate(String unitCode, String coilNo) {
        validateKey(unitCode, coilNo);
        Integer latest = findLatestStored(PostgresUnitCode.uppercase(unitCode), coilNo);
        return latest == null ? allocateNext(unitCode, coilNo) : latest;
    }

    /** 只取同机组同卷序号最大的一行，不修改任何历史记录。 */
    private Integer findLatestStored(String storedUnitCode, String coilNo) {
        LambdaQueryWrapper<QmDcRepeatProdNoLogEntity> query = Wrappers
                .<QmDcRepeatProdNoLogEntity>lambdaQuery()
                .eq(QmDcRepeatProdNoLogEntity::getUnitCode, storedUnitCode)
                .eq(QmDcRepeatProdNoLogEntity::getInMatNo, coilNo)
                .orderByDesc(QmDcRepeatProdNoLogEntity::getInMatRepeatProdNo)
                .last("LIMIT 1");
        QmDcRepeatProdNoLogEntity latest = baseMapper.selectOne(query);
        return latest == null ? null : latest.getInMatRepeatProdNo();
    }

    /** 表中的业务键不可为空，取号前先校验。 */
    private void validateKey(String unitCode, String coilNo) {
        if (unitCode == null || unitCode.trim().isEmpty()
                || coilNo == null || coilNo.trim().isEmpty()) {
            throw new IllegalArgumentException("查询钢卷生产次数失败: 机组编码和钢卷号不能为空");
        }
    }
}
