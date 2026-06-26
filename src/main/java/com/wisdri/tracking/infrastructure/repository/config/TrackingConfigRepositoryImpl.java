package com.wisdri.tracking.infrastructure.repository.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import com.wisdri.tracking.infrastructure.dto.feign.cube.ConvertedTrackingConfig;
import com.wisdri.tracking.infrastructure.service.feign.gateway.CubeApiGateway;
import com.wisdri.tracking.infrastructure.service.redis.RedisKeys;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 的跟踪配置仓储实现。
 */
@Repository
public class TrackingConfigRepositoryImpl implements TrackingConfigRepository {
    /**
     * 配置缓存。
     */
    private final Map<String, TrackingConfig> cache = new ConcurrentHashMap<>();

    /**
     * 跟踪类型与配置模型类型映射。
     */
    private final Map<TrackingType, Class<? extends TrackingConfig>> configTypes = configTypes();

    /**
     * 配置 JSON 反序列化器。
     */
    private final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapperBuilder()
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
     * 从本地缓存读取配置对象引用。
     */
    @Override
    public Optional<TrackingConfig> find(String unitCode, TrackingType trackingType) {
        return Optional.ofNullable(cache.get(configKey(unitCode, trackingType)));
    }

    /**
     * 从 Cube API 同步配置到 Redis，再从 Redis 刷新本地缓存对象。
     */
    @Override
    public void refresh() {
        List<ConvertedTrackingConfig> configs = syncCubeApiConfigToRedis();
        for (ConvertedTrackingConfig config : configs) {
            refreshLocalCacheFromRedis(config.getUnitCode(), config.getTrackingType());
        }
    }

    /**
     * 从 Cube API 获取配置树并写入 Redis。
     */
    private List<ConvertedTrackingConfig> syncCubeApiConfigToRedis() {
        List<ConvertedTrackingConfig> configs = cubeApiGateway.fetchTrackingConfigs();
        for (ConvertedTrackingConfig config : configs) {
            String key = configKey(config.getUnitCode(), config.getTrackingType());
            try {
                stringRedisTemplate.opsForValue().set(key, JsonUtils.toPrettyJson(objectMapper, config.getConfig()));
            } catch (IOException e) {
                throw new TrackingException("写入跟踪配置到 Redis 失败: " + key, e);
            }
        }
        return configs;
    }

    /**
     * 从 Redis 刷新本地缓存对象。
     */
    private void refreshLocalCacheFromRedis(String unitCode, TrackingType trackingType) {
        String key = configKey(unitCode, trackingType);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.trim().isEmpty()) {
            cache.get(key);
            return;
        }
        TrackingConfig refreshedConfig = readConfig(unitCode, trackingType, json);
        TrackingConfig cachedConfig = cache.get(key);
        if (cachedConfig == null) {
            cache.put(key, refreshedConfig);
            return;
        }
        copyConfig(refreshedConfig, cachedConfig);
    }

    /**
     * 构造 Redis 配置 key。
     */
    private String configKey(String unitCode, TrackingType trackingType) {
        return RedisKeys.trackingConfig(unitCode, trackingType);
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
