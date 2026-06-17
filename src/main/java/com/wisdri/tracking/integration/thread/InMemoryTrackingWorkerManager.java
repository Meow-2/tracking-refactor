package com.wisdri.tracking.integration.thread;

import com.wisdri.tracking.application.tracking.TrackingWorkerManager;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存型跟踪工作流管理器。
 *
 * <p>真实 MQTT 动态订阅接入前，先记录每个跟踪工作流的启动状态。</p>
 */
@Slf4j
@Component
public class InMemoryTrackingWorkerManager implements TrackingWorkerManager {
    /**
     * 已启动工作流记录，key 为 unitCode + trackingType，value 为 MQTT topic。
     */
    private final Map<String, String> runningWorkers = new ConcurrentHashMap<>();

    /**
     * 记录指定工作流已启动。
     */
    @Override
    public void start(String unitCode, TrackingType trackingType, String mqttTopic) {
        runningWorkers.put(key(unitCode, trackingType), mqttTopic);
        log.info("启动跟踪工作流，unitCode={}, trackingType={}, mqttTopic={}", unitCode, trackingType, mqttTopic);
    }

    /**
     * 记录指定工作流已停止。
     */
    @Override
    public void stop(String unitCode, TrackingType trackingType) {
        runningWorkers.remove(key(unitCode, trackingType));
        log.info("停止跟踪工作流，unitCode={}, trackingType={}", unitCode, trackingType);
    }

    /**
     * 构造工作流唯一标识。
     */
    private String key(String unitCode, TrackingType trackingType) {
        return unitCode + ":" + trackingType.name();
    }
}
