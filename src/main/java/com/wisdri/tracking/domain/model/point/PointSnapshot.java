package com.wisdri.tracking.domain.model.point;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 一次点位数据快照。
 *
 * <p>通常由一条 MQTT 消息转换而来，包含点位值集合和接收时间。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointSnapshot {
    /**
     * 点位值集合，key 通常使用点位短名，value 为统一封装后的点位值。
     */
    private Map<String, PointValue> values;

    /**
     * 快照接收时间，内部统一使用 Instant 便于写入时序数据。
     */
    private Instant receivedAt;

    /**
     * 按点位编码读取点位值。
     */
    public Optional<PointValue> value(String pointCode) {
        if (values == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(pointCode));
    }
}
