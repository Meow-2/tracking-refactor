package com.wisdri.tracking.integration.redis;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Redis key 构造器。
 *
 * <p>统一维护跟踪配置和上一条点位快照的 Redis key 规则。</p>
 */
@Component
public class RedisKeyBuilder {
    /**
     * Redis key 前缀。
     */
    private static final String PREFIX = "tracking";

    /**
     * 配置 key 后缀。
     */
    private static final String CONFIG = "config";

    /**
     * 上一条点位快照 key 后缀。
     */
    private static final String LAST_DATA = "lastdata";

    /**
     * Redis key 分隔符。
     */
    private static final String SEPARATOR = ":";

    /**
     * 构造跟踪配置 key。
     */
    public String configKey(String unitCode, TrackingType trackingType) {
        return build(unitCode, trackingType, CONFIG);
    }

    /**
     * 构造上一条点位快照 key。
     */
    public String lastDataKey(String unitCode, TrackingType trackingType) {
        return build(unitCode, trackingType, LAST_DATA);
    }

    /**
     * 组装 Redis key。
     */
    private String build(String unitCode, TrackingType trackingType, String suffix) {
        return String.join(SEPARATOR,
                PREFIX,
                normalize(unitCode),
                trackingType.getCode(),
                suffix);
    }

    /**
     * 统一 Redis key 中的文本大小写。
     */
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
