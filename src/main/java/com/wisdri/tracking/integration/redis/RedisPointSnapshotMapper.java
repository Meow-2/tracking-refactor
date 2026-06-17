package com.wisdri.tracking.integration.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis 上一条点位快照 JSON 映射器。
 */
@Component
public class RedisPointSnapshotMapper {
    /**
     * Redis lastdata JSON 对象类型。
     */
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    /**
     * JSON 序列化工具。
     */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 将 Redis lastdata JSON 转换为点位快照。
     */
    public PointSnapshot fromJson(String json) {
        try {
            Map<String, Object> rawValues = objectMapper.readValue(json, MAP_TYPE);
            Map<String, PointValue> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
                values.put(entry.getKey(), PointValue.of(entry.getValue()));
            }
            return PointSnapshot.builder()
                    .values(values)
                    .receivedAt(Instant.now())
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Redis 点位快照 JSON 解析失败", e);
        }
    }

    /**
     * 将点位快照转换为 Redis lastdata JSON。
     */
    public String toJson(PointSnapshot snapshot) {
        try {
            Map<String, Object> rawValues = new LinkedHashMap<>();
            if (snapshot.getValues() != null) {
                for (Map.Entry<String, PointValue> entry : snapshot.getValues().entrySet()) {
                    rawValues.put(entry.getKey(), entry.getValue().getRawValue());
                }
            }
            return objectMapper.writeValueAsString(rawValues);
        } catch (IOException e) {
            throw new IllegalArgumentException("Redis 点位快照 JSON 序列化失败", e);
        }
    }
}
