package com.wisdri.tracking.infrastructure.repository.abnormal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.abnormal.AbnormalType;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.abnormal.AbnormalDataRepository;
import com.wisdri.tracking.infrastructure.dto.postgres.abnormal.AbnormalDataEntity;
import com.wisdri.tracking.infrastructure.repository.PostgresUnitCode;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.postgres.abnormal.AbnormalDataMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 PostgreSQL 的异常数据仓储实现。
 */
@Repository
public class AbnormalDataRepositoryImpl extends ServiceImpl<AbnormalDataMapper, AbnormalDataEntity>
        implements AbnormalDataRepository {
    /**
     * 异常数据 mapper。
     */
    @Resource
    private AbnormalDataMapper abnormalDataMapper;

    /**
     * 跟踪应用配置。
     */
    @Resource
    private TrackingProperties trackingProperties;

    /**
     * 事务模板，仅在确实需要保存异常数据时开启事务。
     */
    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 按机组和跟踪类型查询异常数据。
     */
    @Override
    public List<AbnormalData> find(String unitCode, TrackingType trackingType) {
        LambdaQueryWrapper<AbnormalDataEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AbnormalDataEntity::getUnitCode, PostgresUnitCode.uppercase(unitCode))
                .eq(AbnormalDataEntity::getTrackingType,
                        trackingType == null ? null : trackingType.getCode());
        List<AbnormalDataEntity> entities = abnormalDataMapper.selectList(queryWrapper);
        List<AbnormalData> result = new ArrayList<>();
        if (entities == null || entities.isEmpty()) {
            return result;
        }
        for (AbnormalDataEntity entity : entities) {
            result.add(toDomain(entity));
        }
        return result;
    }

    /**
     * 保存一批异常数据。
     */
    @Override
    public void save(List<AbnormalData> abnormalData) {
        if (trackingProperties != null && !trackingProperties.abnormalStorageEnabled()) {
            return;
        }
        if (abnormalData == null || abnormalData.isEmpty()) {
            return;
        }
        List<AbnormalDataEntity> entities = new ArrayList<>(abnormalData.size());
        for (AbnormalData data : abnormalData) {
            entities.add(toEntity(data));
        }
        transactionTemplate.execute(status -> {
            saveBatch(entities);
            return null;
        });
    }

    private AbnormalDataEntity toEntity(AbnormalData data) {
        AbnormalDataEntity entity = new AbnormalDataEntity();
        entity.setUnitCode(PostgresUnitCode.uppercase(data.getUnitCode()));
        entity.setTrackingType(data.getTrackingType() == null ? null : data.getTrackingType().getCode());
        entity.setPointCode(data.getPointCode());
        entity.setAbnormalType(data.getAbnormalType() == null ? null : data.getAbnormalType().getCode());
        entity.setRawValue(data.getRawValue() == null ? null : String.valueOf(data.getRawValue()));
        entity.setReason(data.getReason());
        entity.setOccurredAt(data.getOccurredAt());
        return entity;
    }

    private AbnormalData toDomain(AbnormalDataEntity entity) {
        return AbnormalData.builder()
                .unitCode(entity.getUnitCode())
                .trackingType(trackingType(entity.getTrackingType()))
                .pointCode(entity.getPointCode())
                .abnormalType(abnormalType(entity.getAbnormalType()))
                .rawValue(entity.getRawValue())
                .reason(entity.getReason())
                .occurredAt(entity.getOccurredAt())
                .build();
    }

    private TrackingType trackingType(String code) {
        if (code == null) {
            return null;
        }
        for (TrackingType trackingType : TrackingType.values()) {
            if (code.equals(trackingType.getCode())) {
                return trackingType;
            }
        }
        return null;
    }

    private AbnormalType abnormalType(String code) {
        if (code == null) {
            return null;
        }
        for (AbnormalType abnormalType : AbnormalType.values()) {
            if (code.equals(abnormalType.getCode())) {
                return abnormalType;
            }
        }
        return null;
    }
}
