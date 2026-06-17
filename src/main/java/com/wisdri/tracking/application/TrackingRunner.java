package com.wisdri.tracking.application;

import com.wisdri.tracking.application.config.TrackingConfigCacheService;
import com.wisdri.tracking.application.tracking.TrackingWorkerManager;
import com.wisdri.tracking.common.config.TrackingProperties;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * 程序启动入口。
 *
 * <p>Spring Boot 应用启动完成后，读取当前机组配置并启动已启用的跟踪工作流。</p>
 */
@Slf4j
@Component
public class TrackingRunner implements ApplicationRunner {
    /**
     * 当前服务实例配置。
     */
    @Resource
    private TrackingProperties trackingProperties;

    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingConfigRepository configRepository;

    /**
     * 跟踪配置缓存。
     */
    @Resource
    private TrackingConfigCacheService configCache;

    /**
     * 跟踪工作流管理器。
     */
    @Resource
    private TrackingWorkerManager workerManager;

    /**
     * Spring Boot 启动完成后的入口方法。
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("开始启动跟踪服务");
        String unitCode = trackingProperties.getUnit();
        for (TrackingType trackingType : TrackingType.values()) {
            Optional<TrackingConfig> configOptional = configRepository.find(unitCode, trackingType);
            if (!configOptional.isPresent()) {
                continue;
            }
            TrackingConfig config = configOptional.get();
            configCache.put(config);
            if (Boolean.TRUE.equals(config.getEnable())) {
                workerManager.start(unitCode, trackingType, config.getMqttTopic());
            }
        }
        log.info("跟踪启动编排完成，unitCode={}", unitCode);
    }
}
