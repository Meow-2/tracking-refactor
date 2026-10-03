package com.wisdri.tracking.infrastructure.repository;

import java.util.Locale;

/** PostgreSQL 机组编码的存取约定；仅在关系库存储边界使用，不改变跟踪任务中的编码。 */
public final class PostgresUnitCode {
    private PostgresUnitCode() {
    }

    /** 统一为与语言环境无关的大写；空值原样保留，由各业务入口自行校验。 */
    public static String uppercase(String unitCode) {
        return unitCode == null ? null : unitCode.toUpperCase(Locale.ROOT);
    }
}
