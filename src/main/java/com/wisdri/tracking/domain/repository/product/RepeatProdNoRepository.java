package com.wisdri.tracking.domain.repository.product;

/**
 * 钢卷重复生产次数仓储，以机组编码和钢卷号确定一个计数对象。
 */
public interface RepeatProdNoRepository {
    /** 首次分配返回 1，此后逐次插入并返回新次数；存储失败时抛出异常。 */
    Integer allocateNext(String unitCode, String coilNo);

    /** 仅读取已分配的最大次数；记录不存在时返回 null。 */
    Integer findLatest(String unitCode, String coilNo);
}
