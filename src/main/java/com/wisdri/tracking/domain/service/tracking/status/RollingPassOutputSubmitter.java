package com.wisdri.tracking.domain.service.tracking.status;

import com.wisdri.tracking.domain.model.tracking.status.RollingPassOutput;

/** 将已结算道次快速提交给异步发送端；不得在调用线程执行 HTTP 请求。 */
public interface RollingPassOutputSubmitter {
    /** 队列满时记录失败并直接返回，不能阻塞 status 跟踪。 */
    void submit(RollingPassOutput output);
}
