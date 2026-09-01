package com.wisdri.tracking.infrastructure.repository.tracking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.coiler.CoilerResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.infrastructure.dto.postgres.coiler.QmCoilerLogEntity;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.postgres.coiler.QmCoilerLogMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 基于 PostgreSQL 的开卷卷取结果仓储。
 */
@Repository
public class CoilerTrackingResultRepositoryImpl
        extends ServiceImpl<QmCoilerLogMapper, QmCoilerLogEntity>
        implements TrackingResultRepository<TrackingConfig, CoilerResult> {
    @Resource
    private TrackingProperties trackingProperties;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.COILER == trackingType;
    }

    @Override
    public void createTable(TrackingConfig config) {
        // qm_dc_coiler_log 由数据库脚本预建。
    }

    @Override
    public void save(List<CoilerResult> results) {
        if (!trackingProperties.coilerStorageEnabled() || results == null || results.isEmpty()) {
            return;
        }
        transactionTemplate.execute(status -> {
            for (CoilerResult result : results) {
                upsert(result);
            }
            return null;
        });
    }

    /**
     * 同一物料号、重复生产号和道次号只保留一条记录，后续事件更新对应侧的方式和设备字段。
     */
    private void upsert(CoilerResult result) {
        String productNo = result.getInMatNoProdNo() == null
                ? null : String.valueOf(result.getInMatNoProdNo());
        LambdaQueryWrapper<QmCoilerLogEntity> query = Wrappers.<QmCoilerLogEntity>lambdaQuery()
                .eq(QmCoilerLogEntity::getInMatNo, result.getInMatNo());
        if (productNo == null) {
            query.isNull(QmCoilerLogEntity::getInMatNoProdNo);
        } else {
            query.eq(QmCoilerLogEntity::getInMatNoProdNo, productNo);
        }
        if (result.getPassNo() == null) {
            query.isNull(QmCoilerLogEntity::getPassNo);
        } else {
            query.eq(QmCoilerLogEntity::getPassNo, result.getPassNo());
        }
        QmCoilerLogEntity entity = baseMapper.selectOne(query
                .orderByAsc(QmCoilerLogEntity::getId)
                .last("LIMIT 1"));
        if (entity == null) {
            baseMapper.insert(toEntity(result));
            return;
        }
        merge(entity, result);
        baseMapper.updateById(entity);
    }

    private QmCoilerLogEntity toEntity(CoilerResult result) {
        QmCoilerLogEntity entity = new QmCoilerLogEntity();
        merge(entity, result);
        Instant createTime = result.getReceivedAt() == null
                ? result.getGeneratedAt() : result.getReceivedAt();
        entity.setCreateTime(createTime);
        return entity;
    }

    private void merge(QmCoilerLogEntity entity, CoilerResult result) {
        entity.setUnitCode(result.getUnitCode());
        entity.setInMatNo(result.getInMatNo());
        entity.setInMatNoProdNo(result.getInMatNoProdNo() == null
                ? null : String.valueOf(result.getInMatNoProdNo()));
        entity.setPassNo(result.getPassNo());
        if (result.getCoilerMethod() != null || result.getCoilerMethodName() != null) {
            entity.setCoilerMethod(result.getCoilerMethod());
            entity.setCoilerMethodName(result.getCoilerMethodName());
            entity.setCoilerDeviceCode(result.getCoilerDeviceCode());
            entity.setCoilerDeviceName(result.getCoilerDeviceName());
            entity.setCoilerMaxLength(decimalText(result.getCoilerMaxLength()));
        }
        if (result.getUncoilerMethod() != null || result.getUncoilerMethodName() != null) {
            entity.setUncoilerMethod(result.getUncoilerMethod());
            entity.setUncoilerMethodName(result.getUncoilerMethodName());
            entity.setUncoilerDeviceCode(result.getUncoilerDeviceCode());
            entity.setUncoilerDeviceName(result.getUncoilerDeviceName());
            entity.setUncoilerMaxLength(decimalText(result.getUncoilerMaxLength()));
        }
        if (result.getCoilerMethod() == null && result.getCoilerMethodName() == null
                && result.getUncoilerMethod() == null && result.getUncoilerMethodName() == null) {
            throw new IllegalArgumentException("开卷或卷取方式不能为空");
        }
    }

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
