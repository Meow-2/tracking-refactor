package com.wisdri.tracking.infrastructure.dto.feign.cube;

import lombok.Data;

/**
 * Cube API 多维度树查询请求。
 */
@Data
public class CubeApiTreeRequest {
    /**
     * 查询层级。
     */
    private Integer level;

    /**
     * 参数范围。
     */
    private Integer paraRange;

    /**
     * 查询路径。
     */
    private String path;

    /**
     * 是否返回路径头。
     */
    private Boolean pathHeader;

    /**
     * 是否只返回目录。
     */
    private Boolean onlyDir;

    /**
     * 构造默认配置树请求。
     */
    public static CubeApiTreeRequest defaultRequest(String path) {
        CubeApiTreeRequest request = new CubeApiTreeRequest();
        request.setLevel(null);
        request.setParaRange(2);
        request.setPath(path);
        request.setPathHeader(false);
        request.setOnlyDir(false);
        return request;
    }
}
