package com.wisdri.tracking.domain.repository.tracking;

import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 跟踪结果仓储分发器。
 */
@Component
@Slf4j
public class TrackingResultRepositoryDispatcher {
    /**
     * 单一跟踪结果类型仓储。
     */
    @Resource
    private List<TrackingResultRepository<? extends TrackingConfig, ? extends TrackingResult>> repositories;

    /**
     * 创建或更新所有支持的跟踪结果表。
     */
    @SuppressWarnings("unchecked")
    public <C extends TrackingConfig> void createTable(C config, PointSnapshot latestSnapshot) {
        if (config == null || config.getTrackingType() == null) {
            throw new IllegalArgumentException("跟踪配置和跟踪类型不能为空");
        }
        if (repositories == null || repositories.isEmpty()) {
            return;
        }
        for (TrackingResultRepository<? extends TrackingConfig, ? extends TrackingResult> repository : repositories) {
            if (repository.support(config.getTrackingType())) {
                ((TrackingResultRepository<C, ? extends TrackingResult>) repository).createTable(config, latestSnapshot);
                return;
            }
        }
        throw new TrackingException("不支持的跟踪结果表类型: " + config.getTrackingType());
    }

    /**
     * 根据结果类型分发到对应仓储。
     */
    @SuppressWarnings("unchecked")
    public <T extends TrackingResult> void save(List<T> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        if (repositories == null || repositories.isEmpty()) {
            return;
        }
        if (results.get(0).getTrackingType() == null) {
            throw new IllegalArgumentException("跟踪结果类型不能为空");
        }
        for (TrackingResultRepository<? extends TrackingConfig, ? extends TrackingResult> repository : repositories) {
            if (repository.support(results.get(0).getTrackingType())) {
                ((TrackingResultRepository<? extends TrackingConfig, T>) repository).save(results);
                return;
            }
        }
        throw new TrackingException("不支持的跟踪结果类型: " + results.get(0).getTrackingType());
    }
}
