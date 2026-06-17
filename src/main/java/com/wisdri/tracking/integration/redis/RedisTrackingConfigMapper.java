package com.wisdri.tracking.integration.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.LengthMode;
import com.wisdri.tracking.domain.model.config.RollingConfig;
import com.wisdri.tracking.domain.model.config.SegmentConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.TrackingSection;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis 配置 JSON 映射器。
 *
 * <p>负责在 Redis snake_case JSON 和领域层 TrackingConfig 之间转换。</p>
 */
@Component
public class RedisTrackingConfigMapper {
    /**
     * JSON 序列化工具。
     */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 将 Redis 中的配置 JSON 转换为领域配置对象。
     */
    public TrackingConfig fromJson(String unitCode, TrackingType trackingType, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return TrackingConfig.builder()
                    .unitCode(unitCode)
                    .trackingType(trackingType)
                    .enable(booleanValue(root.get("enable")))
                    .mqttTopic(text(root.get("mqtt_topic")))
                    .tracking(toTrackingSection(root.get("tracking")))
                    .segments(toSegments(root.get("segments")))
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Redis 跟踪配置 JSON 解析失败", e);
        }
    }

    /**
     * 将领域配置对象转换为 Redis 可存储 JSON。
     */
    public String toJson(TrackingConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (IOException e) {
            throw new IllegalArgumentException("Redis 跟踪配置 JSON 序列化失败", e);
        }
    }

    /**
     * 转换 tracking 节点。
     */
    private TrackingSection toTrackingSection(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return TrackingSection.builder()
                .pointPrefix(text(node.get("point_prefix")))
                .speedPoint(text(node.get("speed_point")))
                .startCondition(toStartCondition(node.get("start_condition")))
                .lengthMode(toLengthMode(text(node.get("length_mode"))))
                .rolling(toRollingConfig(node.get("rolling")))
                .points(toPointGroups(node.get("points")))
                .build();
    }

    /**
     * 转换启动条件节点。
     */
    private StartCondition toStartCondition(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return StartCondition.builder()
                .point(text(node.get("point")))
                .threshold(decimal(node.get("threshold")))
                .build();
    }

    /**
     * 转换轧机配置节点。
     */
    private RollingConfig toRollingConfig(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return RollingConfig.builder()
                .directPoint(text(node.get("direct_point")))
                .passNoPoint(text(node.get("pass_no_point")))
                .directReverse(booleanValue(node.get("direct_reverse")))
                .build();
    }

    /**
     * 转换跟踪点位组数组。
     */
    private List<TrackingPointGroup> toPointGroups(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TrackingPointGroup> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(TrackingPointGroup.builder()
                    .lengthPoints(stringList(item.get("length")))
                    .coilNoPoint(text(item.get("coil_no")))
                    .rollingCoiler(booleanValue(item.get("is_rolling_coiler")))
                    .build());
        }
        return result;
    }

    /**
     * 转换工艺段数组。
     */
    private List<SegmentConfig> toSegments(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<SegmentConfig> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(SegmentConfig.builder()
                    .name(text(item.get("name")))
                    .pointPrefix(text(item.get("point_prefix")))
                    .lengthCorrect(decimal(item.get("length_correct")))
                    .lengthArrayIndex(integer(item.get("length_array_index")))
                    .points(stringList(item.get("points")))
                    .build());
        }
        return result;
    }

    /**
     * 将 JSON 字符串或数组统一转换为字符串列表。
     */
    private List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                result.add(text(item));
            }
        } else {
            result.add(text(node));
        }
        return result;
    }

    /**
     * 根据配置中的 code 转换长度模式枚举。
     */
    private LengthMode toLengthMode(String code) {
        if (code == null) {
            return null;
        }
        for (LengthMode mode : LengthMode.values()) {
            if (mode.getCode().equalsIgnoreCase(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知长度模式: " + code);
    }

    /**
     * 读取 JSON 文本值。
     */
    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /**
     * 读取 JSON 数字值。
     */
    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }

    /**
     * 读取 JSON 整数值。
     */
    private Integer integer(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    /**
     * 读取 JSON 布尔值。
     */
    private Boolean booleanValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asBoolean();
    }
}
