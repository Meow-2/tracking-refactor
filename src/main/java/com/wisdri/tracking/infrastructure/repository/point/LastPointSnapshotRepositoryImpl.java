package com.wisdri.tracking.infrastructure.repository.point;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.point.LastPointSnapshotRepository;
import com.wisdri.tracking.infrastructure.service.redis.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Optional;

/**
 * 基于 Redis 的上一条点位快照仓储实现。
 */
@Repository
public class LastPointSnapshotRepositoryImpl implements LastPointSnapshotRepository {
    /**
     * 快照 JSON 序列化器。
     */
    private final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapperBuilder()
            .addModule(new JavaTimeModule())
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /**
     * Redis 客户端。
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询指定机组和跟踪类型的上一条点位快照。
     */
    @Override
    public Optional<PointSnapshot> find(String unitCode, TrackingType trackingType) {
        return find(unitCode, trackingType, null);
    }

    @Override
    public Optional<PointSnapshot> find(String unitCode,
                                        TrackingType trackingType,
                                        String templateCode) {
        String key = snapshotKey(unitCode, trackingType, templateCode);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PointSnapshot.class));
        } catch (IOException e) {
            throw new TrackingException("读取上一条点位快照失败: " + key, e);
        }
    }

    /**
     * 保存指定机组和跟踪类型的最新点位快照，供下一次消息处理使用。
     */
    @Override
    public void save(String unitCode, TrackingType trackingType, PointSnapshot snapshot) {
        save(unitCode, trackingType, null, snapshot);
    }

    @Override
    public void save(String unitCode,
                     TrackingType trackingType,
                     String templateCode,
                     PointSnapshot snapshot) {
        String key = snapshotKey(unitCode, trackingType, templateCode);
        try {
            stringRedisTemplate.opsForValue().set(key, JsonUtils.toPrettyJson(objectMapper, snapshot));
        } catch (IOException e) {
            throw new TrackingException("保存上一条点位快照失败: " + key, e);
        }
    }

    /**
     * 构造 Redis 上一条点位快照 key。
     */
    private String snapshotKey(String unitCode, TrackingType trackingType) {
        return snapshotKey(unitCode, trackingType, null);
    }

    private String snapshotKey(String unitCode, TrackingType trackingType, String templateCode) {
        return RedisKeys.lastPointSnapshot(unitCode, trackingType, templateCode);
    }
}
