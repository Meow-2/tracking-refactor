package com.wisdri.tracking.infrastructure.repository.runtime;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingConfig;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepository;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.service.feign.gateway.CubeApiGateway;
import com.wisdri.tracking.infrastructure.service.redis.RedisKeys;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 和本地缓存的跟踪配置、算法运行态仓储实现。
 */
@Repository
@Slf4j
public class TrackingRuntimeRepositoryImpl implements TrackingRuntimeRepository {
    /** status 运行态仅在最近一帧距当前时间不超过一分钟时允许跨进程恢复。 */
    private static final Duration STATUS_RESTORE_MAX_AGE = Duration.ofMinutes(1);

    /**
     * 配置缓存。
     */
    private final Map<String, TrackingConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 算法运行态本地缓存。
     */
    private final Map<String, TrackingRuntime> runtimeCache = new ConcurrentHashMap<>();

    /** 每个 status key 只尝试从 Redis 恢复一次，避免过期数据被反复读取。 */
    private final Set<String> statusRestoreAttempted = ConcurrentHashMap.newKeySet();

    /** 恢复时效的时间源，测试中可固定时间以覆盖一分钟边界。 */
    private Clock clock = Clock.systemUTC();

    /**
     * 跟踪类型与配置模型类型映射。
     */
    private final Map<TrackingType, Class<? extends TrackingConfig>> configTypes = configTypes();

    /**
     * 配置和运行态 JSON 序列化器。
     */
    private final ObjectMapper objectMapper = JsonUtils.shanghaiTimeDisplayMapperBuilder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    /**
     * Redis 客户端。
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Cube API 访问网关。
     */
    @Resource
    private CubeApiGateway cubeApiGateway;

    /**
     * 跟踪结果仓储分发器。
     */
    @Resource
    private TrackingResultRepositoryDispatcher trackingResultRepositoryDispatcher;

    /**
     * 当前仓储负责已完整接入的跟踪类型。
     */
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.PROCESS == trackingType
                || TrackingType.BATCH == trackingType
                || TrackingType.STATUS == trackingType
                || TrackingType.SHEAR == trackingType
                || TrackingType.TRIMMING == trackingType;
    }

    /**
     * 从本地缓存读取配置对象引用。
     */
    @Override
    public Optional<TrackingConfig> findConfig(String unitCode, TrackingType trackingType) {
        return Optional.ofNullable(configCache.get(configKey(unitCode, trackingType)));
    }

    /** 从本地缓存读取运行态；status 首次读取时可从 Redis 恢复一分钟内的状态。 */
    @Override
    public Optional<TrackingRuntime> findRuntime(String unitCode, TrackingType trackingType) {
        return findRuntime(unitCode, trackingType, null);
    }

    /** 从本地缓存读取运行态；仅 status 支持在缓存未命中时恢复。 */
    @Override
    public Optional<TrackingRuntime> findRuntime(String unitCode,
                                                  TrackingType trackingType,
                                                  String templateCode) {
        validateTemplateCode(trackingType, templateCode);
        String key = runtimeKey(unitCode, trackingType, templateCode);
        TrackingRuntime cached = runtimeCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        return TrackingType.STATUS == trackingType
                ? restoreStatusRuntime(key, unitCode) : Optional.empty();
    }

    /**
     * Redis 恢复仅用于进程首次读取 status；无效、过期或损坏的历史状态不影响重新采样。
     * 同步保护首次读取，避免并发消息在恢复完成前看到部分初始化状态。
     */
    private synchronized Optional<TrackingRuntime> restoreStatusRuntime(String key, String unitCode) {
        TrackingRuntime cached = runtimeCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (!statusRestoreAttempted.add(key)) {
            return Optional.empty();
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.trim().isEmpty()) {
                return Optional.empty();
            }
            StatusTrackingRuntime restored = objectMapper.readValue(json, StatusTrackingRuntime.class);
            if (!unitCode.equalsIgnoreCase(restored.getUnitCode())
                    || restored.getTrackingType() != TrackingType.STATUS || !recentStatus(restored)
                    || !compatibleStatusConfig(unitCode, restored)) {
                log.info("跳过过期、身份不匹配或与当前配置不兼容的 status 运行态，key={}，receivedAt={}",
                        key, restored.getReceivedAt());
                return Optional.empty();
            }
            // 保存新点位帧可能与恢复并发，已产生的新状态优先于 Redis 旧快照。
            TrackingRuntime existing = runtimeCache.putIfAbsent(key, restored);
            if (existing != null) {
                return Optional.of(existing);
            }
            log.info("已从 Redis 恢复 status 运行态，key={}，receivedAt={}", key, restored.getReceivedAt());
            return Optional.of(restored);
        } catch (IOException | RuntimeException e) {
            log.warn("读取 Redis status 运行态失败，将从新点位重新采样，key={}", key, e);
            return Optional.empty();
        }
    }

    /** 仅接受收到时间在当前时刻之前、且年龄不超过一分钟的 status 帧。 */
    private boolean recentStatus(StatusTrackingRuntime runtime) {
        Instant receivedAt = runtime.getReceivedAt();
        if (receivedAt == null) {
            return false;
        }
        Duration age = Duration.between(receivedAt, clock.instant());
        return !age.isNegative() && age.compareTo(STATUS_RESTORE_MAX_AGE) <= 0;
    }

    /** 防止部署时设备列表或换向配置改变后，恢复上一版本的设备窗口和当前侧别。 */
    private boolean compatibleStatusConfig(String unitCode, StatusTrackingRuntime runtime) {
        TrackingConfig config = configCache.get(configKey(unitCode, TrackingType.STATUS));
        if (!(config instanceof StatusTrackingConfig)) {
            return true;
        }
        StatusTrackingSection tracking = ((StatusTrackingConfig) config).getTracking();
        if (tracking == null || tracking.getPoints() == null) {
            return true;
        }
        Set<String> deviceCodes = new HashSet<>();
        for (StatusPointGroup group : tracking.getPoints()) {
            deviceCodes.add(group.getCode());
        }
        if (runtime.getCandidates() != null && !runtime.getCandidates().isEmpty()
                && !deviceCodes.equals(runtime.getCandidates().keySet())) {
            return false;
        }
        if (runtime.getCurrent() != null) {
            for (StatusCurrentRuntime current : runtime.getCurrent().values()) {
                if (current != null && current.getDeviceCode() != null
                        && !deviceCodes.contains(current.getDeviceCode())) {
                    return false;
                }
            }
        }
        if (tracking.getRolling() == null) {
            return runtime.getRollingDirectReverse() == null;
        }
        return Boolean.valueOf(Boolean.TRUE.equals(tracking.getRolling().getDirectReverse()))
                .equals(runtime.getRollingDirectReverse());
    }

    /**
     * 先整体替换本地运行态，再同步写入 Redis 供外部查看。
     */
    @Override
    public void saveRuntime(TrackingRuntime runtime) {
        saveRuntimes(java.util.Collections.singletonList(runtime));
    }

    /**
     * 先校验并序列化整批状态，再一次性替换本地缓存引用并写 Redis。
     * Redis 客户端没有跨 key 事务保证；写入失败时本地状态仍保留，可阻止已入库事件重复计算。
     */
    @Override
    public void saveRuntimes(java.util.List<? extends TrackingRuntime> runtimes) {
        if (runtimes == null || runtimes.isEmpty()) {
            return;
        }
        Map<String, String> serialized = new LinkedHashMap<>();
        Map<String, TrackingRuntime> pendingCache = new LinkedHashMap<>();
        for (TrackingRuntime runtime : runtimes) {
            if (runtime == null || runtime.getUnitCode() == null || runtime.getTrackingType() == null) {
                throw new TrackingException("保存跟踪运行态失败: 缺少机组或跟踪类型");
            }
            String instanceCode = runtimeInstanceCode(runtime);
            validateInstanceCode(runtime.getTrackingType(), instanceCode);
            String key = runtimeKey(runtime.getUnitCode(), runtime.getTrackingType(), instanceCode);
            try {
                String json = TrackingType.STATUS == runtime.getTrackingType()
                        ? JsonUtils.toPrettyJsonWithInlineArrays(objectMapper, runtime)
                        : JsonUtils.toPrettyJson(objectMapper, runtime);
                serialized.put(key, json);
                pendingCache.put(key, runtime);
            } catch (IOException e) {
                throw new TrackingException("序列化跟踪运行态失败: " + key, e);
            }
        }
        runtimeCache.putAll(pendingCache);
        try {
            for (Map.Entry<String, String> entry : serialized.entrySet()) {
                stringRedisTemplate.opsForValue().set(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException e) {
            throw new TrackingException("批量写入跟踪运行态到 Redis 失败", e);
        }
    }

    /**
     * 从 Cube API 同步配置到 Redis，再从 Redis 刷新本地缓存对象。
     */
    @Override
    public void refreshConfig() {
        Map<TrackingType, TrackingConfig> configs = syncCubeApiConfigToRedis();
        for (Map.Entry<TrackingType, TrackingConfig> entry : configs.entrySet()) {
            TrackingConfig config = entry.getValue();
            refreshLocalCacheFromRedis(config.getUnitCode(), entry.getKey())
                    .ifPresent(refreshedConfig -> trackingResultRepositoryDispatcher.createTable(refreshedConfig));
        }
    }

    /**
     * 从 Cube API 获取配置树并写入 Redis。
     */
    private Map<TrackingType, TrackingConfig> syncCubeApiConfigToRedis() {
        Map<TrackingType, TrackingConfig> fetchedConfigs = cubeApiGateway.fetchTrackingConfigs();
        Map<TrackingType, TrackingConfig> supportedConfigs = new EnumMap<>(TrackingType.class);
        for (Map.Entry<TrackingType, TrackingConfig> entry : fetchedConfigs.entrySet()) {
            // 只同步已经完整接入配置、算法和结果存储的跟踪类型。
            if (!support(entry.getKey())) {
                continue;
            }
            TrackingConfig config = entry.getValue();
            String key = configKey(config.getUnitCode(), entry.getKey());
            try {
                stringRedisTemplate.opsForValue().set(key, JsonUtils.toPrettyJson(objectMapper, config));
                supportedConfigs.put(entry.getKey(), config);
            } catch (IOException e) {
                throw new TrackingException("写入Cube配置到 Redis 失败: " + key, e);
            }
        }
        return supportedConfigs;
    }

    /**
     * 从 Redis 刷新本地缓存对象。
     */
    private Optional<TrackingConfig> refreshLocalCacheFromRedis(String unitCode, TrackingType trackingType) {
        String key = configKey(unitCode, trackingType);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.trim().isEmpty()) {
            return Optional.empty();
        }
        TrackingConfig refreshedConfig = readConfig(unitCode, trackingType, json);
        TrackingConfig cachedConfig = configCache.get(key);
        if (cachedConfig == null) {
            configCache.put(key, refreshedConfig);
            return Optional.of(refreshedConfig);
        }
        copyConfig(refreshedConfig, cachedConfig);
        return Optional.of(cachedConfig);
    }

    /**
     * 构造 Redis 配置 key。
     */
    private String configKey(String unitCode, TrackingType trackingType) {
        return RedisKeys.trackingConfig(unitCode, trackingType);
    }

    private String runtimeKey(String unitCode, TrackingType trackingType, String templateCode) {
        return RedisKeys.trackingRuntime(unitCode, trackingType, templateCode);
    }

    /**
     * 批次运行态必须绑定具体模板实例，避免多个炉台误用同一个 Redis key。
     */
    private void validateTemplateCode(TrackingType trackingType, String templateCode) {
        if (TrackingType.BATCH == trackingType
                && (templateCode == null || templateCode.trim().isEmpty())) {
            throw new TrackingException("读写批次跟踪运行态失败: templateCode 不能为空");
        }
    }

    /**
     * batch 以模板代码隔离，shear 以 device_code 隔离，其他类型保持单实例。
     */
    private String runtimeInstanceCode(TrackingRuntime runtime) {
        if (TrackingType.SHEAR == runtime.getTrackingType()) {
            if (!(runtime instanceof ShearTrackingRuntime)) {
                throw new TrackingException("保存剪切运行态失败: runtime 类型不匹配");
            }
            return ((ShearTrackingRuntime) runtime).getDeviceCode();
        }
        return runtime.getTemplateCode();
    }

    private void validateInstanceCode(TrackingType trackingType, String instanceCode) {
        validateTemplateCode(trackingType, instanceCode);
        if (TrackingType.SHEAR == trackingType
                && (instanceCode == null || instanceCode.trim().isEmpty())) {
            throw new TrackingException("读写剪切跟踪运行态失败: deviceCode 不能为空");
        }
    }

    /**
     * 根据跟踪类型读取配置。
     */
    private TrackingConfig readConfig(String unitCode, TrackingType trackingType, String json) {
        Class<? extends TrackingConfig> configType = configTypes.get(trackingType);
        if (configType == null) {
            throw new TrackingException("不支持的跟踪配置类型: " + trackingType);
        }
        try {
            TrackingConfig config = objectMapper.readValue(json, configType);
            config.setUnitCode(unitCode);
            config.setTrackingType(trackingType);
            return config;
        } catch (IOException e) {
            throw new TrackingException("读取跟踪配置失败: " + configKey(unitCode, trackingType), e);
        }
    }

    /**
     * 注册各跟踪类型对应的配置模型类型。
     */
    private Map<TrackingType, Class<? extends TrackingConfig>> configTypes() {
        Map<TrackingType, Class<? extends TrackingConfig>> types = new EnumMap<>(TrackingType.class);
        types.put(TrackingType.PROCESS, ProcessTrackingConfig.class);
        types.put(TrackingType.BATCH, BatchTrackingConfig.class);
        types.put(TrackingType.STATUS, StatusTrackingConfig.class);
        types.put(TrackingType.SHEAR, ShearTrackingConfig.class);
        types.put(TrackingType.TRIMMING, TrimmingTrackingConfig.class);
        return types;
    }

    /**
     * 将刷新后的配置复制到缓存对象，保持缓存引用不变。
     */
    private void copyConfig(TrackingConfig refreshedConfig, TrackingConfig cachedConfig) {
        if (!cachedConfig.getClass().equals(refreshedConfig.getClass())) {
            throw new TrackingException("缓存配置类型和刷新配置类型不一致: "
                    + cachedConfig.getClass().getName()
                    + " -> "
                    + refreshedConfig.getClass().getName());
        }
        BeanUtils.copyProperties(refreshedConfig, cachedConfig);
    }
}
