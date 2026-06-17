package com.wisdri.tracking.application.retracking.impl;

import com.wisdri.tracking.application.retracking.ReTrackingApplicationService;
import com.wisdri.tracking.application.retracking.command.ReTrackingCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 默认数据重跟踪应用服务。
 *
 * <p>历史时序数据读取接口接入前，该实现只记录请求。</p>
 */
@Slf4j
@Service
public class ReTrackingApplicationServiceImpl implements ReTrackingApplicationService {
    /**
     * 接收重跟踪请求。
     */
    @Override
    public void retrack(ReTrackingCommand command) {
        log.info("收到重跟踪请求: {}", command);
    }
}
