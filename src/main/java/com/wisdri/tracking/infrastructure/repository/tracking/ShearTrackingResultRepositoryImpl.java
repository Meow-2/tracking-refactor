package com.wisdri.tracking.infrastructure.repository.tracking;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.infrastructure.dto.postgres.shear.QmShearLogEntity;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.postgres.shear.QmShearLogMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 PostgreSQL 的剪切跟踪结果仓储。
 */
@Repository
public class ShearTrackingResultRepositoryImpl
        extends ServiceImpl<QmShearLogMapper, QmShearLogEntity>
        implements TrackingResultRepository<ShearTrackingConfig, ShearResult> {
    /** 控制剪切结果是否写入 PostgreSQL，关闭时 save 直接返回。 */
    @Resource
    private TrackingProperties trackingProperties;

    /** 保证同一消费任务产生的全部剪切记录在一个 PostgreSQL 事务中批量提交。 */
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.SHEAR == trackingType;
    }

    @Override
    public void createTable(ShearTrackingConfig config) {
        // qm_shear_log 由数据库脚本预建。
    }

    @Override
    public void save(List<ShearResult> results) {
        if (!trackingProperties.shearStorageEnabled() || results == null || results.isEmpty()) {
            return;
        }
        List<QmShearLogEntity> entities = new ArrayList<>(results.size());
        for (ShearResult result : results) {
            entities.add(toEntity(result));
        }
        transactionTemplate.execute(status -> {
            saveBatch(entities);
            return null;
        });
    }

    /**
     * 将领域结果映射为关系表字段；shearKind 和 shearPointCode 仅服务算法/runtime，不落表。
     */
    private QmShearLogEntity toEntity(ShearResult result) {
        QmShearLogEntity entity = new QmShearLogEntity();
        entity.setUnitCode(result.getUnitCode());
        entity.setInMatNo(result.getInMatNo());
        entity.setInMatNoProdNo(result.getInMatNoProdNo());
        entity.setShearType(result.getShearType());
        entity.setShearLength(result.getShearLength());
        entity.setSetNumber(result.getSetNumber());
        entity.setShearTime(result.getShearTime());
        entity.setPorCoilNo(result.getPorCoilNo());
        entity.setPorColorCode(result.getPorColorCode());
        entity.setTrCoilNo(result.getTrCoilNo());
        entity.setTrColorCode(result.getTrColorCode());
        entity.setPorRemainLength(result.getPorRemainLength());
        entity.setPorMaxLength(result.getPorMaxLength());
        entity.setTrRemainLength(result.getTrRemainLength());
        entity.setTrMaxLength(result.getTrMaxLength());
        entity.setCutNo(result.getCutNo());
        entity.setShearNo(result.getShearNo());
        return entity;
    }
}
