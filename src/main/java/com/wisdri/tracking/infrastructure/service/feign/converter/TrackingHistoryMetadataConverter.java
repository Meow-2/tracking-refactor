package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.history.TrackingHistoryMetadata;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 按机组和跟踪类型定位配置节点，并解析该节点的配置及子树点位历史元信息。 */
@Component
public class TrackingHistoryMetadataConverter {
    @Resource
    private List<CubeApiTrackingConfigConverter> converters;

    @Resource
    private PointHistoryMetadataConverter pointMetadataConverter;

    /** 历史入口严格校验，配置转换异常直接向调用方传播。 */
    public TrackingHistoryMetadata convert(CubeApiTreeResponse tree, String unitCode,
                                           TrackingType trackingType, String treeRoot) {
        if (unitCode == null || unitCode.trim().isEmpty() || trackingType == null) {
            throw new TrackingException("历史元信息查询需要机组和跟踪类型");
        }
        if (tree == null || treeRoot == null || treeRoot.trim().isEmpty()) {
            throw new TrackingException("Cube 配置树或根路径为空");
        }
        String normalizedUnit = unitCode.trim().toLowerCase(Locale.ROOT);
        Map.Entry<String, CubeApiTreeNode> unit = uniqueChild(tree.getRoots(), normalizedUnit, "机组");
        Map.Entry<String, CubeApiTreeNode> type = uniqueChild(
                unit.getValue().getChildren(), trackingType.getCode(), "跟踪类型");
        String unitPath = path(unit.getValue(), join(treeRoot, unit.getKey()));
        String typePath = path(type.getValue(), join(unitPath, type.getKey()));
        List<ConfigNode> candidates = new ArrayList<>();
        collectConfigs(type.getValue(), typePath, candidates);
        if (candidates.isEmpty()) {
            throw new TrackingException("缺少 " + trackingType.getCode() + " 基础配置: " + normalizedUnit);
        }
        if (candidates.size() != 1) {
            throw new TrackingException("Cube 跟踪配置节点重复: " + normalizedUnit + " " + trackingType);
        }
        ConfigNode selected = candidates.get(0);
        TrackingConfig config = converter(trackingType).convert(normalizedUnit, selected.node);
        if (config == null) {
            throw new TrackingException("Cube 跟踪配置转换结果为空: " + normalizedUnit + " " + trackingType);
        }
        return new TrackingHistoryMetadata(config,
                pointMetadataConverter.convert(selected.node, selected.path, "cube"));
    }

    /** 当前节点有配置时使用其完整子树；其下的工艺段配置属于该配置的一部分。 */
    private void collectConfigs(CubeApiTreeNode node, String treePath, List<ConfigNode> candidates) {
        String nodePath = path(node, treePath);
        JsonNode config = node.getData() == null ? null : node.getData().get("default");
        if (config != null && !config.isNull()) {
            if (!config.isObject()) {
                throw new TrackingException("Cube data.default 配置必须是对象: " + nodePath);
            }
            candidates.add(new ConfigNode(node, nodePath));
            return;
        }
        for (Map.Entry<String, CubeApiTreeNode> child : node.getChildren().entrySet()) {
            if (child.getValue() != null && !Integer.valueOf(2).equals(child.getValue().getItemType())) {
                collectConfigs(child.getValue(), join(nodePath, child.getKey()), candidates);
            }
        }
    }

    private CubeApiTrackingConfigConverter converter(TrackingType trackingType) {
        CubeApiTrackingConfigConverter found = null;
        if (converters != null) {
            for (CubeApiTrackingConfigConverter converter : converters) {
                if (converter.support(trackingType)) {
                    if (found != null) {
                        throw new TrackingException("Cube 跟踪类型转换器重复: " + trackingType);
                    }
                    found = converter;
                }
            }
        }
        if (found == null) {
            throw new TrackingException("未注册 Cube 跟踪类型转换器: " + trackingType);
        }
        return found;
    }

    private Map.Entry<String, CubeApiTreeNode> uniqueChild(Map<String, CubeApiTreeNode> children,
                                                         String code, String kind) {
        Map.Entry<String, CubeApiTreeNode> found = null;
        for (Map.Entry<String, CubeApiTreeNode> entry : children.entrySet()) {
            if (code.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                if (found != null) {
                    throw new TrackingException("Cube " + kind + "节点重复: " + code);
                }
                found = entry;
            }
        }
        if (found == null) {
            throw new TrackingException("Cube " + kind + "节点不存在: " + code);
        }
        return found;
    }

    private String path(CubeApiTreeNode node, String fallback) {
        return node.getPath() == null || node.getPath().trim().isEmpty() ? fallback : node.getPath();
    }

    private String join(String parent, String child) {
        return parent.endsWith("/") ? parent + child : parent + "/" + child;
    }

    private static class ConfigNode {
        private final CubeApiTreeNode node;
        private final String path;

        private ConfigNode(CubeApiTreeNode node, String path) {
            this.node = node;
            this.path = path;
        }
    }
}
