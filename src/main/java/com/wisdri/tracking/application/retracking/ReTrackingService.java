package com.wisdri.tracking.application.retracking;

import com.wisdri.tracking.application.retracking.command.ReTrackingCommand;

/**
 * 数据重跟踪应用服务。
 *
 * <p>负责按时间范围读取历史点位数据，并复用在线跟踪流程重新生成跟踪结果。</p>
 */
public interface ReTrackingService {
    /**
     * 执行一次重跟踪。
     */
    void retrack(ReTrackingCommand command);
}
