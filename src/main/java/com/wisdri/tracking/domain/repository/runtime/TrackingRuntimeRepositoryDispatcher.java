package com.wisdri.tracking.domain.repository.runtime;

import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * 跟踪配置和算法运行态仓储分发器。
 */
@Component
public class TrackingRuntimeRepositoryDispatcher {
    /**
     * 各跟踪类型的运行态仓储。
     */
    @Resource
    private List<TrackingRuntimeRepository> repositories;

    /**
     * 从对应仓储读取配置。
     */
    public Optional<TrackingConfig> findConfig(String unitCode, TrackingType trackingType) {
        return repository(trackingType)
                .flatMap(repository -> repository.findConfig(unitCode, trackingType));
    }

    /**
     * 按指定配置类型读取配置。
     */
    public <T extends TrackingConfig> Optional<T> findConfigAs(String unitCode,
                                                               TrackingType trackingType,
                                                               Class<T> configType) {
        return repository(trackingType)
                .flatMap(repository -> repository.findConfigAs(unitCode, trackingType, configType));
    }

    /**
     * 从对应仓储读取算法运行态。
     */
    public Optional<TrackingRuntime> findRuntime(String unitCode, TrackingType trackingType) {
        return repository(trackingType)
                .flatMap(repository -> repository.findRuntime(unitCode, trackingType));
    }

    /**
     * 按指定运行态类型读取算法运行态。
     */
    public <T extends TrackingRuntime> Optional<T> findRuntimeAs(String unitCode,
                                                                 TrackingType trackingType,
                                                                 Class<T> runtimeType) {
        return repository(trackingType)
                .flatMap(repository -> repository.findRuntimeAs(unitCode, trackingType, runtimeType));
    }

    /**
     * 将运行态分发到支持其跟踪类型的仓储。
     */
    public void saveRuntime(TrackingRuntime runtime) {
        if (runtime == null || runtime.getTrackingType() == null) {
            throw new IllegalArgumentException("跟踪运行态和跟踪类型不能为空");
        }
        TrackingRuntimeRepository repository = repository(runtime.getTrackingType())
                .orElseThrow(() -> new TrackingException("不支持的跟踪运行态类型: " + runtime.getTrackingType()));
        repository.saveRuntime(runtime);
    }

    /**
     * 刷新所有运行态仓储管理的配置。
     */
    public void refreshConfig() {
        if (repositories == null) {
            return;
        }
        for (TrackingRuntimeRepository repository : repositories) {
            repository.refreshConfig();
        }
    }

    private Optional<TrackingRuntimeRepository> repository(TrackingType trackingType) {
        if (trackingType == null || repositories == null) {
            return Optional.empty();
        }
        return repositories.stream()
                .filter(repository -> repository.support(trackingType))
                .findFirst();
    }
}
