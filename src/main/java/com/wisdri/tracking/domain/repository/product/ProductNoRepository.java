package com.wisdri.tracking.domain.repository.product;

/**
 * 钢卷生产次数仓储，以机组编码和钢卷号确定一个计数对象。
 */
public interface ProductNoRepository {
    /** 首次分配返回 1，此后原子递增并返回新次数；存储失败时抛出异常。 */
    Integer incrementAndGet(String unitCode, String coilNo);

    /** 仅读取已分配的次数；记录不存在时返回 null。 */
    Integer findCurrent(String unitCode, String coilNo);
}
