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
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.trimming.TrimmingTrackingConfig;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepository;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.service.feign.gateway.CubeApiGateway;
import com.wisdri.tracking.infrastructure.service.redis.RedisKeys;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 和本地缓存的跟踪配置、算法运行态仓储实现。
 */
@Repository
public class TrackingRuntimeRepositoryImpl implements TrackingRuntimeRepository {
    /**
     * 配置缓存。
     */
    private final Map<String, TrackingConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 算法运行态本地缓存。
     */
    private final Map<String, TrackingRuntime> runtimeCache = new ConcurrentHashMap<>();

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

    /**
     * 从本地缓存读取运行态；进程启动后不从 Redis 恢复历史运行态。
     */
    @Override
    public Optional<TrackingRuntime> findRuntime(String unitCode, TrackingType trackingType) {
        return findRuntime(unitCode, trackingType, null);
    }

    /**
     * 从本地缓存读取模板实例运行态；进程启动后不从 Redis 恢复历史运行态。
     */
    @Override
    public Optional<TrackingRuntime> findRuntime(String unitCode,
                                                  TrackingType trackingType,
                                                  String templateCode) {
        validateTemplateCode(trackingType, templateCode);
        String key = runtimeKey(unitCode, trackingType, templateCode);
        return Optional.ofNullable(runtimeCache.get(key));
    }

    /**
     * 先整体替换本地运行态，再同步写入 Redis 供外部查看。
     */
    @Override
    public void saveRuntime(TrackingRuntime runtime) {
        if (runtime == null || runtime.getUnitCode() == null || runtime.getTrackingType() == null) {
            throw new TrackingException("保存跟踪运行态失败: 缺少机组或跟踪类型");
        }
        String instanceCode = runtimeInstanceCode(runtime);
        validateInstanceCode(runtime.getTrackingType(), instanceCode);
        String key = runtimeKey(runtime.getUnitCode(), runtime.getTrackingType(), instanceCode);
        runtimeCache.put(key, runtime);
        try {
            String json = TrackingType.STATUS == runtime.getTrackingType()
                    ? JsonUtils.toPrettyJsonWithInlineArrays(objectMapper, runtime)
                    : JsonUtils.toPrettyJson(objectMapper, runtime);
            stringRedisTemplate.opsForValue().set(key, json);
        } catch (IOException e) {
            throw new TrackingException("写入跟踪运行态到 Redis 失败: " + key, e);
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
