package com.wisdri.tracking.infrastructure.service.feign.gateway.timeseries.history;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.history.*;
import com.wisdri.tracking.infrastructure.service.feign.client.timeseries.history.TimeSeriesHistoryFeignClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.function.Supplier;

/** 历史只读查询网关，失败直接传播，不增加本地分段或缓存。 */
@Component
public class TimeSeriesHistoryGateway {
    @Resource
    private TimeSeriesHistoryFeignClient timeSeriesHistoryFeignClient;

    /** 获取全部状态区间。 */
    public List<State> queryStates(StatesRequest request) {
        return read(() -> timeSeriesHistoryFeignClient.queryStates(request));
    }

    /** 查询点位值大于 min 的首末帧。 */
    public Bounds queryBounds(BoundsRequest request) {
        return read(() -> timeSeriesHistoryFeignClient.queryBounds(request));
    }

    /** 获取跨库、跨表点位的最近值。 */
    public List<Latest> queryLatest(LatestRequest request) {
        return read(() -> timeSeriesHistoryFeignClient.queryLatest(request));
    }

    private <T> T read(Supplier<R<T>> query) {
        R<T> result;
        try {
            result = query.get();
        } catch (RuntimeException e) {
            throw new ExternalServiceException("quality-ts 历史查询请求失败");
        }
        if (!R.isSuccess(result) || result.getData() == null) {
            throw new ExternalServiceException("quality-ts 历史查询返回失败或缺少数据");
        }
        return result.getData();
    }
}
