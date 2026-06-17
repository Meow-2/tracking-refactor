package com.wisdri.tracking.integration.redis;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.port.point.LastPointSnapshotRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * 基于 Redis 的上一条点位快照仓储实现。
 */
@Repository
public class RedisLastPointSnapshotRepository implements LastPointSnapshotRepository {
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
     * 点位快照 JSON 映射器。
     */
    @Resource
    private RedisPointSnapshotMapper mapper;

    /**
     * 从 Redis 读取上一条点位快照。
     */
    @Override
    public Optional<PointSnapshot> find(String unitCode, TrackingType trackingType) {
        String key = keyBuilder.lastDataKey(unitCode, trackingType);
        String json = redisTemplate.opsForValue().get(key);
        return json == null ? Optional.empty() : Optional.of(mapper.fromJson(json));
    }

    /**
     * 保存上一条点位快照到 Redis。
     */
    @Override
    public void save(String unitCode, TrackingType trackingType, PointSnapshot snapshot) {
        String key = keyBuilder.lastDataKey(unitCode, trackingType);
        redisTemplate.opsForValue().set(key, mapper.toJson(snapshot));
    }
}
