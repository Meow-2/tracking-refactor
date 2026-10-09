package com.wisdri.tracking.infrastructure.repository.tracking;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.trimming.TrimmingResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.infrastructure.dto.postgres.trimming.QmTrimmingLogEntity;
import com.wisdri.tracking.infrastructure.repository.PostgresUnitCode;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.postgres.trimming.QmTrimmingLogMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 基于 PostgreSQL 的切边结果仓储。
 */
@Repository
public class TrimmingTrackingResultRepositoryImpl
        extends ServiceImpl<QmTrimmingLogMapper, QmTrimmingLogEntity>
        implements TrackingResultRepository<TrimmingTrackingConfig, TrimmingResult> {
    @Resource
    private TrackingProperties trackingProperties;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.TRIMMING == trackingType;
    }

    @Override
    public void createTable(TrimmingTrackingConfig config) {
        // qm_dc_trimming_log 由数据库脚本预建。
    }

    @Override
    public void save(List<TrimmingResult> results) {
        if (!trackingProperties.trimmingStorageEnabled() || results == null || results.isEmpty()) {
            return;
        }
        transactionTemplate.execute(status -> {
            for (TrimmingResult result : results) {
                if (Boolean.TRUE.equals(result.getUpdateExisting())) {
                    update(result);
                } else if (exists(result)) {
                    // 重启后切边运行态尚未恢复，数据库中的同卷记录仍应更新。
                    update(result);
                } else {
                    baseMapper.insert(toEntity(result));
                }
            }
            return null;
        });
    }

    /** 插入前按机组、卷号和重复生产次数确认是否已有切边记录。 */
    private boolean exists(TrimmingResult result) {
        return baseMapper.selectCount(Wrappers.<QmTrimmingLogEntity>lambdaQuery()
                .eq(QmTrimmingLogEntity::getUnitCode, PostgresUnitCode.uppercase(result.getUnitCode()))
                .eq(QmTrimmingLogEntity::getInMatNo, result.getInMatNo())
                .eq(QmTrimmingLogEntity::getInMatRepeatProdNo, String.valueOf(result.getRepeatProdNo()))) > 0;
    }

    /** 按业务键更新；记录在查询后被删除时回退为插入。 */
    private void update(TrimmingResult result) {
        QmTrimmingLogEntity entity = toEntity(result);
        entity.setCreateTime(null);
        entity.setUpdateTime(eventTime(result));
        int updated = baseMapper.update(entity, Wrappers.<QmTrimmingLogEntity>lambdaUpdate()
                .eq(QmTrimmingLogEntity::getUnitCode, PostgresUnitCode.uppercase(result.getUnitCode()))
                .eq(QmTrimmingLogEntity::getInMatNo, result.getInMatNo())
                .eq(QmTrimmingLogEntity::getInMatRepeatProdNo, String.valueOf(result.getRepeatProdNo())));
        // 数据库被清理但进程 runtime 尚在时，自愈为插入。
        if (updated == 0) {
            baseMapper.insert(toEntity(result));
        }
    }

    private QmTrimmingLogEntity toEntity(TrimmingResult result) {
        QmTrimmingLogEntity entity = new QmTrimmingLogEntity();
        entity.setUnitCode(PostgresUnitCode.uppercase(result.getUnitCode()));
        entity.setInMatNo(result.getInMatNo());
        entity.setInMatRepeatProdNo(result.getRepeatProdNo() == null
                ? null : String.valueOf(result.getRepeatProdNo()));
        entity.setCoilWidthPv(decimalText(result.getCoilWidthPv()));
        entity.setCoilWidthSv(decimalText(result.getCoilWidthSv()));
        entity.setTrimmingLength(decimalText(result.getTrimmingLength()));
        entity.setCreateTime(eventTime(result));
        entity.setDeleted(0);
        return entity;
    }

    private Instant eventTime(TrimmingResult result) {
        return result.getReceivedAt() == null ? result.getGeneratedAt() : result.getReceivedAt();
    }

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
