package com.wisdri.tracking.infrastructure.service.feign.client.timeseries.history;

import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.history.*;
import com.wisdri.tracking.infrastructure.service.feign.config.timeseries.history.TimeSeriesHistoryFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** 历史查询独立客户端，使用自己的地址及超时配置。 */
@FeignClient(name = "time-series-history", url = "${time-series-history.base-url}",
        configuration = TimeSeriesHistoryFeignConfig.class)
public interface TimeSeriesHistoryFeignClient {
    /** 一次获取全部连续值区间。 */
    @PostMapping("/data/query/states")
    R<List<State>> queryStates(@RequestBody StatesRequest request);

    /** 查询单点位值严格大于 min 的首末帧。 */
    @PostMapping("/data/query/bounds")
    R<Bounds> queryBounds(@RequestBody BoundsRequest request);

    /** 点位可以分布在不同数据库和表。 */
    @PostMapping("/data/query/latest")
    R<List<Latest>> queryLatest(@RequestBody LatestRequest request);
}
