package com.wisdri.tracking.infrastructure.repository.tracking;

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
import java.time.Instant;
import java.util.ArrayList;
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
        // qm_coiler_log 由数据库脚本预建。
    }

    @Override
    public void save(List<CoilerResult> results) {
        if (!trackingProperties.coilerStorageEnabled() || results == null || results.isEmpty()) {
            return;
        }
        List<QmCoilerLogEntity> entities = new ArrayList<>(results.size());
        for (CoilerResult result : results) {
            entities.add(toEntity(result));
        }
        transactionTemplate.execute(status -> {
            saveBatch(entities);
            return null;
        });
    }

    private QmCoilerLogEntity toEntity(CoilerResult result) {
        QmCoilerLogEntity entity = new QmCoilerLogEntity();
        entity.setUnitCode(result.getUnitCode());
        entity.setInMatNo(result.getInMatNo());
        entity.setInMatNoProdNo(result.getInMatNoProdNo() == null
                ? null : String.valueOf(result.getInMatNoProdNo()));
        entity.setCoilerMethod(result.getCoilerMethod());
        entity.setCoilerMethodName(result.getCoilerMethodName());
        entity.setDeviceCode(result.getDeviceCode());
        entity.setDeviceName(result.getDeviceName());
        entity.setMaxLength(result.getMaxLength() == null
                ? null : result.getMaxLength().toPlainString());
        Instant createTime = result.getReceivedAt() == null
                ? result.getGeneratedAt() : result.getReceivedAt();
        entity.setCreateTime(createTime);
        return entity;
    }
}
