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
    /** CSL1 的剪切入料卷号在 PostgreSQL 中仅保留前 11 位。 */
    private static final int CSL1_IN_MAT_NO_LENGTH = 11;

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
        // qm_dc_shear_log 的部署建表脚本位于 docs/sql/qm_dc_shear_log.sql。
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
            if (!saveBatch(entities)) {
                status.setRollbackOnly();
                throw new IllegalStateException("批量写入 qm_dc_shear_log 失败");
            }
            return null;
        });
    }

    /**
     * 将 3.0 领域结果映射为关系表字段；运行态提交对象和剪切点名不落表。
     */
    private QmShearLogEntity toEntity(ShearResult result) {
        QmShearLogEntity entity = new QmShearLogEntity();
        entity.setUnitCode(result.getUnitCode());
        String inMatNo = result.getInMatNo();
        // 只调整 CSL1 的入库字段，领域结果仍保留设备上报的完整卷号供算法使用。
        if ("CSL1".equalsIgnoreCase(result.getUnitCode())
                && inMatNo != null && inMatNo.length() > CSL1_IN_MAT_NO_LENGTH) {
            inMatNo = inMatNo.substring(0, CSL1_IN_MAT_NO_LENGTH);
        }
        entity.setInMatNo(inMatNo);
        entity.setInMatRepeatProdNo(result.getRepeatProdNo());
        entity.setShearType(result.getShearType());
        entity.setShearTypeName(result.getShearTypeName());
        entity.setShearLength(result.getShearLength());
        entity.setSetNumber(result.getSetNumber());
        entity.setShearTime(result.getShearTime());
        entity.setShearDeviceCode(result.getDeviceCode());
        entity.setInMatDeviceCode(result.getInMatDeviceCode());
        entity.setInMatDeviceColorNo(result.getInMatDeviceColorNo());
        entity.setInMatDeviceRemainLength(result.getInMatDeviceRemainLength());
        entity.setInMatDeviceMaxLength(result.getInMatDeviceMaxLength());
        entity.setShearDeviceCoilNo(result.getShearDeviceCoilNo());
        entity.setShearDeviceColorNo(result.getShearDeviceColorNo());
        entity.setShearDeviceRemainLength(result.getShearDeviceRemainLength());
        entity.setShearDeviceMaxLength(result.getShearDeviceMaxLength());
        entity.setCutNo(result.getCutNo());
        entity.setShearNo(result.getShearNo());
        return entity;
    }
}
