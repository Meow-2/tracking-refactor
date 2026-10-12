package com.wisdri.tracking.infrastructure.service.feign.gateway.timeseries.history;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.history.*;
import com.wisdri.tracking.infrastructure.service.feign.client.timeseries.history.TimeSeriesHistoryFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.function.Supplier;

/** 历史只读查询网关，失败直接传播，不增加本地分段或缓存。 */
@Component
@Slf4j
public class TimeSeriesHistoryGateway {
    @Resource
    private TimeSeriesHistoryFeignClient timeSeriesHistoryFeignClient;

    /** 获取全部状态区间。 */
    public List<State> queryStates(StatesRequest request) {
        return read("/data/query/states", request, () -> timeSeriesHistoryFeignClient.queryStates(request));
    }

    /** 查询点位值大于 min 的首末帧。 */
    public Bounds queryBounds(BoundsRequest request) {
        return read("/data/query/bounds", request, () -> timeSeriesHistoryFeignClient.queryBounds(request));
    }

    /** 获取跨库、跨表点位的最近值。 */
    public List<Latest> queryLatest(LatestRequest request) {
        return read("/data/query/latest", request, () -> timeSeriesHistoryFeignClient.queryLatest(request));
    }

    /** 记录只读查询参数和结果概况；历史值不写日志，底层异常仅记录在服务日志中。 */
    private <T> T read(String path, Object request, Supplier<R<T>> query) {
        long startedAt = System.nanoTime();
        log.info("quality-ts 历史查询开始，path={}, request={}", path, request);
        R<T> result;
        try {
            result = query.get();
        } catch (RuntimeException e) {
            log.error("quality-ts 历史查询请求失败，path={}, request={}, elapsedMillis={}",
                    path, request, (System.nanoTime() - startedAt) / 1_000_000L, e);
            throw new ExternalServiceException("quality-ts 历史查询请求失败");
        }
        if (!R.isSuccess(result) || result.getData() == null) {
            log.error("quality-ts 历史查询返回失败或缺少数据，path={}, request={}, elapsedMillis={}, code={}, success={}, message={}",
                    path, request, (System.nanoTime() - startedAt) / 1_000_000L,
                    result == null ? null : result.getCode(), result == null ? null : result.getSuccess(),
                    result == null ? null : result.getMessage());
            throw new ExternalServiceException("quality-ts 历史查询返回失败或缺少数据");
        }
        // 列表只打印条数；Bounds 仅包含首末时间，可以直接打印。
        Object summary = result.getData() instanceof List ? ((List<?>) result.getData()).size() : result.getData();
        log.info("quality-ts 历史查询完成，path={}, elapsedMillis={}, resultCountOrBounds={}",
                path, (System.nanoTime() - startedAt) / 1_000_000L, summary);
        return result.getData();
    }
}
