package com.wisdri.tracking.integration.storage;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.repository.abnormal.AbnormalDataStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 异常数据空存储适配器。
 *
 * <p>PostgreSQL 异常数据表接入前，先记录异常数据数量。</p>
 */
@Slf4j
@Component
public class NoopAbnormalDataStorage implements AbnormalDataStorage {
    /**
     * 暂以日志方式记录异常数据。
     */
    @Override
    public void save(List<AbnormalData> abnormalData) {
        log.warn("检测到异常点位数据，count={}, data={}", abnormalData.size(), abnormalData);
    }
}
