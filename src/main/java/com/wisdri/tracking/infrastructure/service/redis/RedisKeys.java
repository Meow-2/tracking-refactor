package com.wisdri.tracking.infrastructure.service.redis;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Redis key 构造工具。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeys {
    private static final String TRACKING_CONFIG_PATTERN = "tracking:%s:%s:config";
    private static final String TRACKING_RUNTIME_PATTERN = "tracking:%s:%s:runtime";
    private static final String TEMPLATE_TRACKING_RUNTIME_PATTERN = "tracking:%s:%s:%s:runtime";
    private static final String LAST_POINT_SNAPSHOT_PATTERN = "tracking:%s:%s:lastdata";
    private static final String TEMPLATE_LAST_POINT_SNAPSHOT_PATTERN = "tracking:%s:%s:%s:lastdata";

    /**
     * 跟踪配置 key。
     */
    public static String trackingConfig(String unitCode, TrackingType trackingType) {
        return String.format(TRACKING_CONFIG_PATTERN, normalize(unitCode), normalize(trackingType.getCode()));
    }

    /**
     * 跟踪算法运行态 key。
     */
    public static String trackingRuntime(String unitCode, TrackingType trackingType) {
        return String.format(TRACKING_RUNTIME_PATTERN, normalize(unitCode), normalize(trackingType.getCode()));
    }

    /**
     * 按模板实例构造算法运行态 key；模板为空时保持原有 key 格式。
     */
    public static String trackingRuntime(String unitCode,
                                         TrackingType trackingType,
                                         String templateCode) {
        if (templateCode == null || templateCode.trim().isEmpty()) {
            return trackingRuntime(unitCode, trackingType);
        }
        return String.format(TEMPLATE_TRACKING_RUNTIME_PATTERN,
                normalize(unitCode), normalize(trackingType.getCode()), normalize(templateCode));
    }

    /**
     * 上一条点位快照 key。
     */
    public static String lastPointSnapshot(String unitCode, TrackingType trackingType) {
        return String.format(LAST_POINT_SNAPSHOT_PATTERN, normalize(unitCode), normalize(trackingType.getCode()));
    }

    /**
     * 按模板实例构造上一条快照 key；模板为空时保持原有 key 格式。
     */
    public static String lastPointSnapshot(String unitCode,
                                           TrackingType trackingType,
                                           String templateCode) {
        if (templateCode == null || templateCode.trim().isEmpty()) {
            return lastPointSnapshot(unitCode, trackingType);
        }
        return String.format(TEMPLATE_LAST_POINT_SNAPSHOT_PATTERN,
                normalize(unitCode), normalize(trackingType.getCode()), normalize(templateCode));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
