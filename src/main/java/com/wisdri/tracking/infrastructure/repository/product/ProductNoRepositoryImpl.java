package com.wisdri.tracking.infrastructure.repository.product;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wisdri.tracking.domain.repository.product.ProductNoRepository;
import com.wisdri.tracking.infrastructure.service.postgres.product.ProductNoMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

/**
 * 基于 PostgreSQL 的钢卷生产次数仓储，跨实例共享分配结果。
 */
@Repository
public class ProductNoRepositoryImpl implements ProductNoRepository {
    @Resource
    private ProductNoMapper productNoMapper;

    @Override
    public Integer incrementAndGet(String unitCode, String coilNo) {
        validateKey(unitCode, coilNo);
        return productNoMapper.incrementAndGet(IdWorker.getId(), unitCode, coilNo);
    }

    @Override
    public Integer findCurrent(String unitCode, String coilNo) {
        validateKey(unitCode, coilNo);
        return productNoMapper.findCurrent(unitCode, coilNo);
    }

    /** 数据库允许业务键为空，但取号流程只接受有效的机组和钢卷号。 */
    private void validateKey(String unitCode, String coilNo) {
        if (unitCode == null || unitCode.trim().isEmpty()
                || coilNo == null || coilNo.trim().isEmpty()) {
            throw new IllegalArgumentException("查询钢卷生产次数失败: 机组编码和钢卷号不能为空");
        }
    }
}
