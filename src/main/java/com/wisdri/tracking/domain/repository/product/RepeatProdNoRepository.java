package com.wisdri.tracking.domain.repository.product;

/**
 * 钢卷重复生产次数仓储，以机组编码和钢卷号确定一个计数对象。
 */
public interface RepeatProdNoRepository {
    /** 根据全部历史记录分配未使用的下一序号，含已逻辑删除记录；存储失败时抛出异常。 */
    Integer allocateNext(String unitCode, String coilNo);

    /** 仅读取有效记录的最大次数；没有有效记录时返回 1，不写入记录。 */
    Integer findLatest(String unitCode, String coilNo);

    /** 读取有效记录的最大次数；没有有效记录时分配未使用的新序号。 */
    Integer findLatestOrAllocate(String unitCode, String coilNo);
}
