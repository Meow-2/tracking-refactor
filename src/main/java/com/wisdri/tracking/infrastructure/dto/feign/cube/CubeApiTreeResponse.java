package com.wisdri.tracking.infrastructure.dto.feign.cube;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cube API 多维度树响应。
 * <p>
 * 根节点名称由配置动态决定，因此使用名称到节点的映射接收。
 */
public class CubeApiTreeResponse {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 根节点自身的附加数据，不属于业务根目录。
     */
    @Getter
    @Setter
    private JsonNode data;

    /**
     * 以动态根节点编码为键的节点集合，例如 cp1、zrm1。
     */
    private final Map<String, CubeApiTreeNode> roots = new LinkedHashMap<>();

    /**
     * 接收响应中除固定字段以外的动态根节点。
     *
     * @param code 根节点编码
     * @param node 根节点内容
     */
    @JsonAnySetter
    public void addRoot(String code, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        roots.put(code, OBJECT_MAPPER.convertValue(node, CubeApiTreeNode.class));
    }

    /**
     * 获取所有动态根节点。
     */
    @JsonAnyGetter
    public Map<String, CubeApiTreeNode> getRoots() {
        return roots;
    }
}
