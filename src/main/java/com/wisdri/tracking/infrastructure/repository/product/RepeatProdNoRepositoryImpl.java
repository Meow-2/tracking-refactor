package com.wisdri.tracking.infrastructure.repository.product;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wisdri.tracking.domain.repository.product.RepeatProdNoRepository;
import com.wisdri.tracking.infrastructure.service.postgres.product.RepeatProdNoMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 基于 PostgreSQL 的钢卷重复生产次数仓储，逐次保存且跨实例共享分配结果。
 */
@Repository
public class RepeatProdNoRepositoryImpl implements RepeatProdNoRepository {
    @Resource
    private RepeatProdNoMapper repeatProdNoMapper;

    @Override
    @Transactional
    public Integer allocateNext(String unitCode, String coilNo) {
        validateKey(unitCode, coilNo);
        repeatProdNoMapper.lockCoil(unitCode, coilNo);
        Integer latest = repeatProdNoMapper.findLatest(unitCode, coilNo);
        if (latest != null && latest == Integer.MAX_VALUE) {
            throw new IllegalStateException("钢卷重复生产次数已达到整数上限: 机组=" + unitCode + "，钢卷=" + coilNo);
        }
        int next = latest == null ? 1 : latest + 1;
        return repeatProdNoMapper.insert(IdWorker.getId(), unitCode, coilNo, next);
    }

    @Override
    public Integer findLatest(String unitCode, String coilNo) {
        validateKey(unitCode, coilNo);
        return repeatProdNoMapper.findLatest(unitCode, coilNo);
    }

    /** 表中的业务键不可为空，取号前先校验。 */
    private void validateKey(String unitCode, String coilNo) {
        if (unitCode == null || unitCode.trim().isEmpty()
                || coilNo == null || coilNo.trim().isEmpty()) {
            throw new IllegalArgumentException("查询钢卷生产次数失败: 机组编码和钢卷号不能为空");
        }
    }
}
