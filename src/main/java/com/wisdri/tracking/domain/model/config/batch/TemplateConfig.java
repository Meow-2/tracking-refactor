package com.wisdri.tracking.domain.model.config.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批次跟踪模板配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateConfig {
    /**
     * 模板序号占位符。
     */
    private static final String INDEX_PLACEHOLDER = "{index}";

    /**
     * 模板编码，例如 fb{index}。
     */
    private String code;

    /**
     * 模板序号起点，包含该值。
     */
    private Integer indexFrom;

    /**
     * 模板序号终点，包含该值。
     */
    private Integer indexTo;

    /**
     * 按配置的闭区间展开具体模板编码。
     *
     * <p>例如 code=fb{index}、indexFrom=1、indexTo=2 会得到 fb1、fb2。</p>
     *
     * @return 按序号升序排列的不可变模板编码列表
     * @throws IllegalArgumentException 缺少占位符或序号范围无效时抛出
     */
    public List<String> resolveCodes() {
        validate();
        List<String> result = new ArrayList<>();
        for (int index = indexFrom; index <= indexTo; index++) {
            result.add(code.replace(INDEX_PLACEHOLDER, String.valueOf(index)));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 校验模板展开所需的最小配置。
     */
    private void validate() {
        if (code == null || !code.contains(INDEX_PLACEHOLDER)) {
            throw new IllegalArgumentException("批次模板编码必须包含 {index}");
        }
        if (indexFrom == null || indexTo == null) {
            throw new IllegalArgumentException("批次模板序号范围不能为空");
        }
        if (indexFrom > indexTo) {
            throw new IllegalArgumentException("批次模板序号起点不能大于终点");
        }
    }
}
