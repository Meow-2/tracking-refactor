package com.wisdri.tracking.infrastructure.dto.feign.cube;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cube API 多维度树节点。
 * <p>
 * 节点元数据使用固定字段接收，动态目录和点位使用 children 接收。
 */
@Data
public class CubeApiTreeNode {
    /** 节点 ID。 */
    private Long id;
    /** 父节点 ID。 */
    private Long pid;
    /** 节点编码。 */
    private String code;
    /** 节点显示名称。 */
    private String name;
    /** 节点类型，点位节点通常为 2。 */
    private Integer itemType;
    /** 多维度节点唯一键。 */
    private String cubeKey;
    /** 节点完整路径。 */
    private String path;
    /** 分析项元数据。 */
    private Object anaItemMeta;
    /** 节点扩展元数据。 */
    private Object meta;
    /** 关联的采集项 ID。 */
    private Long collectItemId;
    /** 点位值类型，例如 double、boolean、string。 */
    private String valueType;
    /** 点位单位。 */
    private String unit;
    /** 点位标签。 */
    private String tag;
    /** 点位测量标识。 */
    private String measurement;
    /** 节点标记。 */
    private String mark;
    /**
     * 节点附加数据。
     * <p>
     * 不同层级结构不同：配置节点使用 data.default，点位节点可能在此返回
     * valueType，因此保留 JsonNode 以避免把多种结构强行绑定到同一个类。
     */
    private JsonNode data;
    /** 节点是否有效。 */
    private Boolean valid;
    /** 扩展属性一。 */
    private String varAttr1;
    /** 扩展属性二。 */
    private String varAttr2;

    /**
     * 以动态子节点编码为键的节点集合。
     * <p>
     * 机组、跟踪类型、工艺段及点位名称均可能动态变化，不能声明为固定字段。
     */
    private final Map<String, CubeApiTreeNode> children = new LinkedHashMap<>();

    /**
     * 接收节点中除固定元数据字段以外的动态子节点。
     *
     * @param code 子节点编码
     * @param child 子节点内容
     */
    @JsonAnySetter
    public void addChild(String code, CubeApiTreeNode child) {
        children.put(code, child);
    }

    /**
     * 获取所有动态子节点。
     */
    @JsonAnyGetter
    public Map<String, CubeApiTreeNode> getChildren() {
        return children;
    }
}
