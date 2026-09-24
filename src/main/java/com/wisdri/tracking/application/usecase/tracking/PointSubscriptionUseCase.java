package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;

/**
 * 点位消息订阅应用用例。
 */
@Service
public class PointSubscriptionUseCase {
    /**
     * 当前实例跟踪配置。
     */
    @Resource
    private TrackingProperties trackingProperties;

    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingRuntimeRepositoryDispatcher trackingRuntimeRepositoryDispatcher;

    /**
     * MQTT 订阅服务。
     */
    @Resource
    private MqttSubscriptionRegistry mqttSubscriptionRegistry;

    /**
     * 启动当前机组支持的所有点位消息订阅。
     * <p>
     * 启动时先刷新配置缓存，再按 TrackingType 枚举逐一查找当前机组配置。
     * 查到配置后交给 MQTT 订阅注册表注册 topic，后续消息入口再通过 topic
     * 反查到 unitCode 和 trackingType。
     */
    public void start() {
        trackingRuntimeRepositoryDispatcher.refreshConfig();
        // process 类型排在 status 之前订阅；先恢复 status，避免启动瞬间的 process 消息看到空上下文。
        trackingRuntimeRepositoryDispatcher.findConfig(trackingProperties.getUnit(), TrackingType.STATUS)
                .filter(config -> Boolean.TRUE.equals(config.getEnable()))
                .ifPresent(config -> trackingRuntimeRepositoryDispatcher.findRuntimeAs(
                        config.getUnitCode(), TrackingType.STATUS, StatusTrackingRuntime.class));
        for (TrackingType trackingType : TrackingType.values()) {
            Optional<TrackingConfig> configOptional = trackingRuntimeRepositoryDispatcher.findConfig(
                    trackingProperties.getUnit(),
                    trackingType
            );
            configOptional.ifPresent(config -> {
                initializeShearRuntimes(config);
                mqttSubscriptionRegistry.register(config);
            });
        }
    }

    /**
     * shear 开始监控时为每个 device_code 创建独立空运行态，不等待首次剪切信号。
     */
    private void initializeShearRuntimes(TrackingConfig config) {
        if (!(config instanceof ShearTrackingConfig)
                || !Boolean.TRUE.equals(config.getEnable())
                || !trackingProperties.shearStorageEnabled()) {
            return;
        }
        ShearTrackingConfig shearConfig = (ShearTrackingConfig) config;
        if (shearConfig.getTracking() == null) {
            return;
        }
        Map<String, String> deviceNames = statusDeviceNames(config.getUnitCode());
        Map<String, ShearTrackingRuntime> runtimes = new LinkedHashMap<>();
        collectShearRuntimes(runtimes, shearConfig.getTracking().getUncoilerShearPoint(),
                true, config.getUnitCode(), deviceNames);
        collectShearRuntimes(runtimes, shearConfig.getTracking().getCoilerShearPoint(),
                false, config.getUnitCode(), deviceNames);
        for (Map.Entry<String, ShearTrackingRuntime> entry : runtimes.entrySet()) {
            String deviceCode = entry.getKey();
            if (trackingRuntimeRepositoryDispatcher.findRuntimeAs(
                    config.getUnitCode(), TrackingType.SHEAR, deviceCode,
                    ShearTrackingRuntime.class).isPresent()) {
                continue;
            }
            trackingRuntimeRepositoryDispatcher.saveRuntime(entry.getValue());
        }
    }

    private void collectShearRuntimes(Map<String, ShearTrackingRuntime> runtimes,
                                      List<ShearPointConfig> points,
                                      boolean uncoilerSide,
                                      String unitCode,
                                      Map<String, String> deviceNames) {
        if (points == null) {
            return;
        }
        for (ShearPointConfig point : points) {
            if (point == null) {
                continue;
            }
            List<String> codes = point.getDeviceCodes() == null || point.getDeviceCodes().isEmpty()
                    ? Collections.singletonList(point.getDeviceCode()) : point.getDeviceCodes();
            for (String code : codes) {
                if (code != null && !code.trim().isEmpty()) {
                    runtimes.put(code, emptyShearRuntime(
                            unitCode, code, deviceNames.get(code), uncoilerSide));
                }
            }
        }
    }

    private ShearTrackingRuntime emptyShearRuntime(String unitCode,
                                                   String deviceCode,
                                                   String deviceName,
                                                   boolean uncoilerSide) {
        return ShearTrackingRuntime.builder()
                .unitCode(unitCode)
                .trackingType(TrackingType.SHEAR)
                .deviceCode(deviceCode)
                .side(uncoilerSide ? DeviceSide.UNCOILER : DeviceSide.COILER)
                .deviceName(deviceName)
                .head(zeroCounter())
                .slice(zeroCounter())
                .tail(zeroCounter())
                .build();
    }

    /** 新 runtime 显式以 0 表示设备尚未发生对应类型剪切。 */
    private ShearCounterRuntime zeroCounter() {
        return ShearCounterRuntime.builder().shearNo(0).cutNo(0)
                .lastRemainingLength(BigDecimal.ZERO).build();
    }

    private Map<String, String> statusDeviceNames(String unitCode) {
        Map<String, String> names = new LinkedHashMap<>();
        trackingRuntimeRepositoryDispatcher.findConfigAs(
                unitCode, TrackingType.STATUS, StatusTrackingConfig.class)
                .map(StatusTrackingConfig::getTracking)
                .ifPresent(tracking -> {
                    if (tracking.getPoints() != null) {
                        for (StatusPointGroup point : tracking.getPoints()) {
                            if (point != null && point.getCode() != null) {
                                names.put(point.getCode(), point.getName());
                            }
                        }
                    }
                });
        return names;
    }
}
