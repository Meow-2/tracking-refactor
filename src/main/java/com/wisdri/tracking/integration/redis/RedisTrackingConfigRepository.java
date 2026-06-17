package com.wisdri.tracking.integration.redis;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * 基于 Redis 的跟踪配置仓储实现。
 */
@Repository
public class RedisTrackingConfigRepository implements TrackingConfigRepository {
    /**
     * Redis 字符串模板。
     */
    @Resource
    private StringRedisTemplate redisTemplate;

    /**
     * Redis key 构造器。
     */
    @Resource
    private RedisKeyBuilder keyBuilder;

    /**
     * Redis 配置 JSON 映射器。
     */
    @Resource
    private RedisTrackingConfigMapper mapper;

    /**
     * 从 Redis 读取跟踪配置。
     */
    @Override
    public Optional<TrackingConfig> find(String unitCode, TrackingType trackingType) {
        String key = keyBuilder.configKey(unitCode, trackingType);
        String json = redisTemplate.opsForValue().get(key);
        return json == null ? Optional.empty() : Optional.of(mapper.fromJson(unitCode, trackingType, json));
    }

    /**
     * 从 Redis 刷新当前跟踪配置。
     */
    @Override
    public Optional<TrackingConfig> refresh(String unitCode, TrackingType trackingType) {
        return find(unitCode, trackingType);
    }

    /**
     * 将跟踪配置保存到 Redis。
     */
    @Override
    public void save(TrackingConfig config) {
        String key = keyBuilder.configKey(config.getUnitCode(), config.getTrackingType());
        redisTemplate.opsForValue().set(key, mapper.toJson(config));
    }
}
