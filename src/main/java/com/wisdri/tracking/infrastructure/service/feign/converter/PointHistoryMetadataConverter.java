package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.history.PointHistoryMetadata;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 按配置路径解析 Cube 点位历史元数据，供不同机组和跟踪类型复用。 */
@Component
public class PointHistoryMetadataConverter {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern NUMERIC_TYPE = Pattern.compile(
            "short|int|integer|long|float|double|decimal|tinyint|smallint|bigint", Pattern.CASE_INSENSITIVE);

    /** 自动收集配置节点子树中的所有点位，目录节点不生成点位元数据。 */
    public Map<String, PointHistoryMetadata> convert(CubeApiTreeNode root, String rootPath, String database) {
        if (root == null || blank(rootPath)) {
            throw new TrackingException("历史点位转换需要 Cube 子树和根路径");
        }
        List<PointConfig> points = new ArrayList<>();
        collectPoints(root, rootPath, points);
        return convert(root, rootPath, null, points, database);
    }

    private void collectPoints(CubeApiTreeNode node, String treePath, List<PointConfig> points) {
        String path = blank(node.getPath()) ? treePath : node.getPath();
        // 优先采用 Cube 节点类型；旧数据未提供类型时按叶子节点的点位标识识别。
        boolean point = Integer.valueOf(2).equals(node.getItemType())
                || (node.getItemType() == null && node.getChildren().isEmpty()
                && (!blank(node.getCode()) || !blank(node.getCubeKey()))
                && (node.getData() == null || !node.getData().path("default").isObject()));
        if (point) {
            points.add(new PointConfig(path, null));
            return;
        }
        for (Map.Entry<String, CubeApiTreeNode> child : node.getChildren().entrySet()) {
            if (child.getValue() != null) {
                collectPoints(child.getValue(), join(path, child.getKey()), points);
            }
        }
    }

    /**
     * 仅转换调用方指定的点位，无关节点不参与历史映射和类型校验。
     *
     * @param root Cube 子树根节点，可为任意跟踪类型或点位目录
     * @param rootPath 根节点未提供 path 时使用的完整路径
     * @param pointPrefix 配置点位的路径前缀
     * @param requiredPoints 业务所需点位及其期望值类型
     * @param database TDengine 查询数据库名
     * @return 按配置完整路径索引的点位元数据
     */
    public Map<String, PointHistoryMetadata> convert(CubeApiTreeNode root, String rootPath,
                                                    String pointPrefix, Collection<PointConfig> requiredPoints,
                                                    String database) {
        if (root == null || blank(rootPath) || requiredPoints == null) {
            throw new TrackingException("历史点位转换需要 Cube 子树、根路径和点位集合");
        }
        if (blank(database) || !IDENTIFIER.matcher(database).matches()) {
            throw new TrackingException("历史点位 TDengine 数据库名无效: " + database);
        }
        Map<String, List<CubeApiTreeNode>> nodes = new LinkedHashMap<>();
        index(root, rootPath, nodes);
        Map<String, PointHistoryMetadata> points = new LinkedHashMap<>();
        for (PointConfig point : requiredPoints) {
            addPoint(pointPrefix, point, database, nodes, points);
        }
        return points;
    }

    /** 路径元数据缺失时按树层级还原，不按显示名称跨目录猜测点位。 */
    private void index(CubeApiTreeNode node, String treePath, Map<String, List<CubeApiTreeNode>> nodes) {
        String path = blank(node.getPath()) ? treePath : node.getPath();
        nodes.computeIfAbsent(path.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(node);
        for (Map.Entry<String, CubeApiTreeNode> child : node.getChildren().entrySet()) {
            if (child.getValue() != null) {
                index(child.getValue(), join(path, child.getKey()), nodes);
            }
        }
    }

    private void addPoint(String pointPrefix, PointConfig point, String database,
                          Map<String, List<CubeApiTreeNode>> nodes, Map<String, PointHistoryMetadata> points) {
        if (point == null || blank(point.getName())) {
            throw new TrackingException("历史查询所需点位配置为空");
        }
        String path = PointReader.pathResolve(pointPrefix, point.getName());
        List<CubeApiTreeNode> matches = nodes.get(path.toLowerCase(Locale.ROOT));
        if (matches == null || matches.isEmpty()) {
            throw new TrackingException("历史点位不存在: " + path);
        }
        if (matches.size() != 1) {
            throw new TrackingException("历史点位路径重复: " + path);
        }
        CubeApiTreeNode node = matches.get(0);
        String code = node.getCode();
        String cubeKey = node.getCubeKey();
        if (Boolean.FALSE.equals(node.getValid())) {
            throw new TrackingException("历史点位在 Cube 中无效: " + path);
        }
        if (blank(code) || blank(cubeKey) || !cubeKey.endsWith("_" + code)) {
            throw new TrackingException("历史点位 cubeKey 与 code 不匹配: " + path);
        }
        String objectName = cubeKey.substring(0, cubeKey.length() - code.length() - 1);
        if (!IDENTIFIER.matcher(code).matches() || !IDENTIFIER.matcher(objectName).matches()) {
            throw new TrackingException("历史点位 TDengine 对象或字段名无效: " + path);
        }
        String valueType = node.getValueType();
        if (blank(valueType) && node.getData() != null && node.getData().path("valueType").isTextual()) {
            valueType = node.getData().path("valueType").asText();
        }
        validateType(point, valueType, path);
        points.put(path, PointHistoryMetadata.builder().path(blank(node.getPath()) ? path : node.getPath())
                .name(node.getName()).code(code).cubeKey(cubeKey).valueType(valueType)
                .unit(node.getUnit()).valid(node.getValid()).database(database).objectName(objectName).build());
    }

    private void validateType(PointConfig point, String valueType, String path) {
        if (blank(valueType) || point.getType() == null) {
            return;
        }
        if (numeric(point.getType()) && NUMERIC_TYPE.matcher(valueType.trim()).matches()) {
            return;
        }
        PointDataType actual;
        try {
            actual = PointDataType.fromCode(valueType.trim());
        } catch (IllegalArgumentException e) {
            throw new TrackingException("历史点位类型无法识别: " + path, e);
        }
        if (actual != point.getType() && !(numeric(actual) && numeric(point.getType()))) {
            throw new TrackingException("历史点位类型与配置不兼容: " + path);
        }
    }

    private boolean numeric(PointDataType type) {
        return type != PointDataType.BOOLEAN && type != PointDataType.STRING;
    }

    private String join(String parent, String child) {
        return parent.endsWith("/") ? parent + child : parent + "/" + child;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
