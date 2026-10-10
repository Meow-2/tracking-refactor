package com.wisdri.tracking.domain.model.history;

import lombok.Builder;
import lombok.Value;

/**
 * 历史处理所需的点位元数据，建立 Cube 配置点位与 TDengine 查询对象、字段的对应关系。
 * <p>
 * 由历史元信息转换器从 Cube 点位节点生成，保存在 {@link TrackingHistoryMetadata} 中。
 * 后续历史仓储可使用 database、objectName 和 code 定位历史数据；本类不保存历史值。
 */
@Value
@Builder
public class PointHistoryMetadata {
    /** Cube 点位完整路径；节点未提供时按配置树层级还原。 */
    String path;
    /** Cube 点位显示名称。 */
    String name;
    /** Cube 点位编码，同时作为 TDengine 查询字段名。 */
    String code;
    /** Cube 点位唯一键，历史转换要求其格式为 objectName + "_" + code。 */
    String cubeKey;
    /** Cube 声明的值类型，用于校验配置类型兼容性；未提供时可为空。 */
    String valueType;
    /** 点位测量单位，沿用 Cube 元数据。 */
    String unit;
    /** Cube 有效标记；false 会被历史转换器拒绝，null 表示未提供。 */
    Boolean valid;
    /** TDengine 查询数据库名，由调用方指定；Cube 历史元信息入口使用 cube。 */
    String database;
    /** TDengine 查询对象名，通过移除 cubeKey 末尾的 "_" + code 得到。 */
    String objectName;
}
