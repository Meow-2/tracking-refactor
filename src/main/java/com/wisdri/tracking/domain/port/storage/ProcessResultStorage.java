package com.wisdri.tracking.domain.port.storage;

import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;

import java.util.List;

/**
 * 过程跟踪结果存储端口。
 *
 * <p>领域层通过该接口保存算法输出，第一版可以记录日志，后续可替换为数据存储服务。</p>
 */
public interface ProcessResultStorage {
    /**
     * 保存一批过程跟踪结果。
     */
    void save(List<ProcessResult> results);
}
